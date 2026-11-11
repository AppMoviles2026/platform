package com.collabtech.platform.billing.infrastructure.paypal;
import com.collabtech.platform.billing.application.ports.SubscriptionPorts.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

@Component @Profile("!skeleton")
public class PayPalSubscriptions implements Provider {
    private final PayPalGateway api;private final String callback;private final Clock clock;
    public PayPalSubscriptions(PayPalGateway api,@Value("${billing.subscriptions.return-url}")String callback,Clock clock){
        this.api=api;this.callback=callback;this.clock=clock;var uri=URI.create(callback);
        if(uri.getHost()==null||!Set.of("http","https").contains(uri.getScheme())||uri.getQuery()!=null||uri.getFragment()!=null||uri.getUserInfo()!=null)
            throw new IllegalArgumentException("Invalid configured subscription return URL");
    }
    public boolean configured(){return api.configuration().enabled()&&api.configuration().credentialsConfigured();}
    public String returnUrl(){return callback;}
    private static String text(JsonNode n,String key){String v=n.path(key).asText("");if(v.isBlank()||v.length()>128)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);return v;}
    private static String id(String id){if(id==null||!id.matches("[A-Z0-9_-]{1,128}"))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);return id;}
    private static BigDecimal decimal(JsonNode node){try{var value=new BigDecimal(node.asText("0"));if(value.scale()>2||value.precision()>14||value.signum()<0)throw new IllegalArgumentException();return value;}
        catch(Exception e){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}}
    private static Instant instant(JsonNode node){try{return node.isMissingNode()||node.isNull()?null:Instant.parse(node.asText());}catch(Exception e){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}}
    public ProviderPlan plan(String providerId){
        var p=api.request(HttpMethod.GET,"/v1/billing/plans/"+id(providerId),null,null);
        if(!providerId.equals(text(p,"id")))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        var cycles=p.path("billing_cycles");var c=cycles.path(0);var fixed=c.path("pricing_scheme").path("fixed_price");
        boolean supported=cycles.isArray()&&cycles.size()==1&&"REGULAR".equals(c.path("tenure_type").asText())&&c.path("sequence").asInt()==1&&c.path("total_cycles").asInt(-1)==0
            &&"MONTH".equals(c.path("frequency").path("interval_unit").asText())&&c.path("frequency").path("interval_count").asInt()==1
            &&!p.path("quantity_supported").asBoolean(false)&&decimal(p.path("payment_preferences").path("setup_fee").path("value")).signum()==0
            &&decimal(p.path("taxes").path("percentage")).signum()==0;
        return new ProviderPlan(providerId,text(p,"status"),decimal(fixed.path("value")),fixed.path("currency_code").asText(""),supported);
    }
    public Created create(UUID subscription,String providerPlanId,String state){
        return create(subscription,providerPlanId,state,callback);
    }
    public Created create(UUID subscription,String providerPlanId,String state,String frozenReturnUrl){
        String destination=frozenReturnUrl==null?callback:frozenReturnUrl;
        URI target=URI.create(destination);
        if(target.getHost()==null||!Set.of("https","http").contains(target.getScheme())||target.getUserInfo()!=null||target.getQuery()!=null||target.getFragment()!=null)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        String back=destination+"?state="+URLEncoder.encode(state,StandardCharsets.UTF_8);
        var body=Map.of("plan_id",id(providerPlanId),"custom_id",subscription.toString(),"quantity","1",
            "application_context",Map.of("brand_name","CollabPro Sandbox","shipping_preference","NO_SHIPPING",
                "user_action","SUBSCRIBE_NOW","return_url",back,"cancel_url",back+"&cancelled=true"));
        var response=api.request(HttpMethod.POST,"/v1/billing/subscriptions",body,subscription.toString());
        String reference=id(text(response,"id"));String approval=null;
        for(var link:response.path("links"))if("approve".equals(link.path("rel").asText()))approval=link.path("href").asText();
        if(approval!=null){URI uri;try{uri=URI.create(approval);}catch(Exception e){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}
            if(approval.length()>4000||!"https".equals(uri.getScheme())||!Set.of("www.sandbox.paypal.com","sandbox.paypal.com").contains(uri.getHost())
                ||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}
        if(!Set.of("APPROVAL_PENDING","APPROVED","ACTIVE").contains(text(response,"status"))||approval==null)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        return new Created(reference,approval);
    }
    public Remote read(String providerId){
        var s=api.request(HttpMethod.GET,"/v1/billing/subscriptions/"+id(providerId)+"?fields=plan,last_failed_payment",null,null);
        if(!providerId.equals(text(s,"id"))||s.path("plan_overridden").asBoolean(false)||
            (!s.path("quantity").isMissingNode()&&decimal(s.path("quantity")).compareTo(BigDecimal.ONE)!=0)||
            decimal(s.path("shipping_amount").path("value")).signum()!=0)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        String status=text(s,"status");
        if(!Set.of("APPROVAL_PENDING","APPROVED","ACTIVE","SUSPENDED","CANCELLED","EXPIRED").contains(status))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        var billing=s.path("billing_info");Instant next=instant(billing.path("next_billing_time"));
        var charges=new ArrayList<Charge>();
        if("ACTIVE".equals(status)){
            Instant end=clock.instant().truncatedTo(ChronoUnit.SECONDS),start=end.minus(32,ChronoUnit.DAYS);
            String resource="/v1/billing/subscriptions/"+providerId+"/transactions";
            String path=resource+"?start_time="+start+"&end_time="+end;var visited=new HashSet<String>();
            for(int page=0;path!=null;page++){
                if(page>=20||!visited.add(path))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
                var tx=api.request(HttpMethod.GET,path,null,null);
                if(!tx.path("transactions").isArray()||tx.path("total_pages").asInt(1)>20)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
                for(var t:tx.path("transactions")){
                    var amount=t.path("amount_with_breakdown").path("gross_amount");
                    charges.add(new Charge(id(text(t,"id")),text(t,"status"),decimal(amount.path("value")),text(amount,"currency_code"),instant(t.path("time"))));
                }
                path=null;
                for(var link:tx.path("links"))if("next".equals(link.path("rel").asText())){
                    URI uri;try{uri=URI.create(link.path("href").asText());}catch(Exception e){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}
                    if(!"https".equals(uri.getScheme())||!Set.of("api-m.sandbox.paypal.com","api.sandbox.paypal.com").contains(uri.getHost())||
                        uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getFragment()!=null||!resource.equals(uri.getRawPath())||
                        uri.getRawQuery()==null||uri.getRawQuery().length()>4000)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
                    path=uri.getRawPath()+"?"+uri.getRawQuery();
                }
                if(path==null&&tx.path("total_pages").asInt(1)>page+1)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
            }
        }
        return new Remote(providerId,text(s,"custom_id"),text(s,"plan_id"),status,s.path("subscriber").path("payer_id").asText(null),
            instant(!s.path("status_update_time").isMissingNode()?s.path("status_update_time"):!s.path("update_time").isMissingNode()?s.path("update_time"):s.path("create_time")),next,
            billing.path("failed_payments_count").asInt(0),instant(billing.path("last_failed_payment").path("time")),charges);
    }
    public void cancel(String providerId){api.request(HttpMethod.POST,"/v1/billing/subscriptions/"+id(providerId)+"/cancel",Map.of("reason","Cancelación solicitada en CollabPro Sandbox"),null);}
}
