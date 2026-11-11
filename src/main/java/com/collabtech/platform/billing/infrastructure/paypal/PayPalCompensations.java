package com.collabtech.platform.billing.infrastructure.paypal;
import com.collabtech.platform.billing.application.ports.CompensationPorts.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
@Component @Profile("!skeleton")
public class PayPalCompensations implements Provider {
    private final PayPalGateway api;private final Settings settings;
    public PayPalCompensations(PayPalGateway api,@Value("${billing.compensation.enabled:true}")boolean enabled,
        @Value("${billing.compensation.return-url}")String callback,@Value("${billing.compensation.currencies:USD}")String currencies,
        @Value("${billing.compensation.create-retry-seconds:18000}")long orderRetry,@Value("${billing.compensation.payout-retry-seconds:2419200}")long payoutRetry,
        @Value("${billing.compensation.automatic-payouts:true}")boolean automatic){
        this.api=api;URI uri=URI.create(callback);
        if(uri.getHost()==null||!Set.of("https","http").contains(uri.getScheme())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Invalid compensation return URL");
        if(orderRetry<60||orderRetry>18000||payoutRetry<60||payoutRetry>2419200)throw new IllegalArgumentException("Unsafe provider retry window");
        var supported=new HashSet<String>();for(String c:currencies.split(",")){String v=c.strip();if(!Set.of("USD","EUR","GBP","CAD","AUD").contains(v))throw new IllegalArgumentException("Unverified compensation currency");supported.add(v);}
        this.settings=new Settings(enabled&&api.configuration().enabled()&&api.configuration().credentialsConfigured(),Set.copyOf(supported),callback,orderRetry,payoutRetry,automatic);
    }
    public Settings settings(){return settings;}
    public void preflight(){if(!settings.enabled())throw new BillingFailure(Code.PROVIDER_NOT_CONFIGURED);if(!api.hasScope("https://uri.paypal.com/payments/payouts"))throw new BillingFailure(Code.PROVIDER_PERMISSION_REQUIRED);}
    private static String id(String v){if(v==null||!v.matches("[A-Za-z0-9_-]{1,128}"))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);return v;}
    private static String required(JsonNode node,String name){return id(node.path(name).asText(null));}
    private static Instant instant(JsonNode n){try{return n.isMissingNode()||n.isNull()?null:Instant.parse(n.asText());}catch(Exception e){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}}
    private static BigDecimal amount(JsonNode n){try{var a=new BigDecimal(n.asText());if(a.signum()<0||a.scale()>2||a.precision()>14)throw new IllegalArgumentException();return a;}catch(Exception e){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}}
    private static String approval(String url){URI uri;try{uri=URI.create(url);}catch(Exception e){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}
        if(url.length()>4000||!"https".equals(uri.getScheme())||!Set.of("www.sandbox.paypal.com","sandbox.paypal.com").contains(uri.getHost())||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);return url;}
    public Created createOrder(Funding f){
        String back=f.returnUrl()+"?state="+URLEncoder.encode(f.state(),StandardCharsets.UTF_8);
        var unit=Map.of("reference_id",f.operationId().toString(),"custom_id",f.compensationId().toString(),"invoice_id",f.operationId().toString(),
            "amount",Map.of("currency_code",f.currency(),"value",f.amount().toPlainString()));
        var body=Map.of("intent","CAPTURE","purchase_units",List.of(unit),"payment_source",Map.of("paypal",Map.of("experience_context",Map.of(
            "brand_name","CollabPro Sandbox","shipping_preference","NO_SHIPPING","user_action","PAY_NOW","return_url",back,"cancel_url",back+"&cancelled=true"))));
        var result=api.request(HttpMethod.POST,"/v2/checkout/orders",body,f.operationId().toString());String link=null;
        for(var l:result.path("links"))if(Set.of("approve","payer-action").contains(l.path("rel").asText()))link=approval(l.path("href").asText());
        if(link==null)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        return new Created(required(result,"id"),link);
    }
    public Order readOrder(String reference){
        var r=api.request(HttpMethod.GET,"/v2/checkout/orders/"+id(reference),null,null);
        if(!reference.equals(required(r,"id"))||!"CAPTURE".equals(r.path("intent").asText())||!r.path("purchase_units").isArray()||r.path("purchase_units").size()!=1)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        var u=r.path("purchase_units").get(0);var money=u.path("amount");var captures=new ArrayList<Capture>();
        for(var c:u.path("payments").path("captures")){
            var m=c.path("amount");captures.add(new Capture(required(c,"id"),required(c,"status"),amount(m.path("value")),required(m,"currency_code"),
                instant(c.path("update_time").isMissingNode()?c.path("create_time"):c.path("update_time"))));
        }
        return new Order(reference,required(r,"status"),u.path("custom_id").asText(""),u.path("reference_id").asText(""),
            r.path("payer").path("payer_id").asText(r.path("payment_source").path("paypal").path("account_id").asText(null)),
            amount(money.path("value")),required(money,"currency_code"),instant(r.path("update_time").isMissingNode()?r.path("create_time"):r.path("update_time")),captures);
    }
    public void capture(String reference,UUID operation){api.request(HttpMethod.POST,"/v2/checkout/orders/"+id(reference)+"/capture",Map.of(),java.util.UUID.nameUUIDFromBytes(("CAPTURE:"+operation).getBytes(StandardCharsets.UTF_8)).toString());}
    public Payout createPayout(UUID operation,UUID compensation,String receiver,BigDecimal amount,String currency){
        if(!receiver.matches("[A-Z0-9]{13}"))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        var item=Map.of("recipient_type","PAYPAL_ID","receiver",receiver,"amount",Map.of("value",amount.toPlainString(),"currency",currency),
            "sender_item_id",operation.toString(),"note","CollabPro Sandbox "+compensation);
        var body=Map.of("sender_batch_header",Map.of("sender_batch_id",operation.toString()),"items",List.of(item));
        var response=api.payoutRequest(body,operation.toString());
        String batch=required(response.path("batch_header"),"payout_batch_id");
        return readPayout(batch);
    }
    public Payout readPayout(String reference){
        var r=api.request(HttpMethod.GET,"/v1/payments/payouts/"+id(reference)+"?page_size=1000",null,null);
        var h=r.path("batch_header");if(!reference.equals(required(h,"payout_batch_id")))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        var items=r.path("items");
        if(!items.isArray()||items.size()>1||r.path("total_pages").asInt(1)>1)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        String sender=h.path("sender_batch_header").path("sender_batch_id").asText("");
        if(items.isEmpty()){
            String status=required(h,"batch_status");if(!Set.of("PENDING","PROCESSING","DENIED","CANCELED").contains(status))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
            var m=h.path("amount");return new Payout(reference,null,sender,null,null,status,amount(m.path("value")),required(m,"currency"),null,instant(h.path("time_created")));
        }
        return payout(items.get(0),sender,h.path("time_completed").isMissingNode()?instant(h.path("time_created")):instant(h.path("time_completed")));
    }
    public Payout readPayoutItem(String reference){
        var r=api.request(HttpMethod.GET,"/v1/payments/payouts-item/"+id(reference),null,null);if(!reference.equals(required(r,"payout_item_id")))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        String batch=required(r,"payout_batch_id");return readPayout(batch);
    }
    private Payout payout(JsonNode r,String sender,Instant fallback){
        var item=r.path("payout_item");var a=item.path("amount");
        if(!"PAYPAL_ID".equals(item.path("recipient_type").asText()))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        return new Payout(required(r,"payout_batch_id"),required(r,"payout_item_id"),sender,item.path("sender_item_id").asText(""),item.path("receiver").asText(""),
            required(r,"transaction_status"),amount(a.path("value")),required(a,"currency"),r.path("transaction_id").asText(null),r.path("time_processed").isMissingNode()?fallback:instant(r.path("time_processed")));
    }
}
