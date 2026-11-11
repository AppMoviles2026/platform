package com.collabtech.platform.billing.infrastructure.paypal;

import com.collabtech.platform.billing.application.ports.BillingPorts.*;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.client.*;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.*;

@Component @Profile("!skeleton") @EnableConfigurationProperties(PayPalProperties.class)
public class PayPalGateway implements Gateway {
    public static final String API="https://api-m.sandbox.paypal.com";
    private final PayPalProperties settings;private final RestClient http;private final ObjectMapper json;private final Clock clock;
    private java.util.Set<String> appScopes=java.util.Set.of();
    private String appToken;private Instant appTokenExpiry=Instant.EPOCH;
    @org.springframework.beans.factory.annotation.Autowired
    public PayPalGateway(PayPalProperties settings,ObjectMapper json,Clock clock){
        this(settings,json,clock,RestClient.builder().requestFactory(factory()));
    }
    /** Builder overload allows controlled HTTP tests without changing the fixed Sandbox host. */
    public PayPalGateway(PayPalProperties settings,ObjectMapper json,Clock clock,RestClient.Builder builder){
        this.settings=settings;this.json=json;this.clock=clock;this.http=builder.baseUrl(API).build();
    }
    private static JdkClientHttpRequestFactory factory(){
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(10));return factory;
    }
    public Configuration configuration(){return new Configuration(settings.enabled(),settings.credentials(),settings.webhookId()!=null&&!settings.webhookId().isBlank(),
        "SANDBOX",settings.fundingReturnUrl(),settings.identityReturnUrl(),settings.androidReturnUrl(),settings.authorizationSeconds());}
    private void configured(){if(!settings.enabled()||!settings.credentials())throw new BillingFailure(Code.PROVIDER_NOT_CONFIGURED);}
    private static String encode(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static String required(JsonNode n,String key){String value=n.path(key).asText("");
        if(value.isBlank()||value.length()>512)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);return value;}
    private static String identifier(JsonNode n,String key){String v=required(n,key);if(!v.matches("[A-Za-z0-9_-]{1,128}"))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);return v;}
    private JsonNode parse(String body){try{if(body==null||body.length()>262144)throw new IllegalArgumentException();var value=json.readTree(body);
        if(value==null||!value.isObject())throw new IllegalArgumentException();return value;}
        catch(Exception failure){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}}
    private static BillingFailure remote(int code){
        if(code==401)return new BillingFailure(Code.PROVIDER_NOT_CONFIGURED);
        if(code==403)return new BillingFailure(Code.PROVIDER_PERMISSION_REQUIRED);
        if(code==429||code>=500)return new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);
        return new BillingFailure(Code.PROVIDER_REJECTED);
    }
    private JsonNode oauth(LinkedMultiValueMap<String,String> form){
        configured();
        try{return parse(http.post().uri("/v1/oauth2/token").headers(h->h.setBasicAuth(settings.clientId(),settings.clientSecret()))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class));}
        catch(RestClientResponseException e){throw remote(e.getStatusCode().value());}
        catch(ResourceAccessException e){throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);}
    }
    private synchronized String appToken(){if(appToken!=null&&clock.instant().isBefore(appTokenExpiry.minusSeconds(30)))return appToken;
        var form=new LinkedMultiValueMap<String,String>();form.add("grant_type","client_credentials");var result=oauth(form);
        appToken=required(result,"access_token");appScopes=java.util.Set.copyOf(java.util.Arrays.asList(result.path("scope").asText("").split("\\s+")));long ttl=result.path("expires_in").asLong(0);
        if(ttl<60||ttl>86400)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);appTokenExpiry=clock.instant().plusSeconds(ttl);return appToken;}
    synchronized boolean hasScope(String scope){appToken();return appScopes.contains(scope);}
    JsonNode request(HttpMethod method,String path,Object body,String requestId){
        configured();
        for(int attempt=0;attempt<2;attempt++){
            String bearer=appToken();
            try{var spec=http.method(method).uri(path).headers(h->{h.setBearerAuth(bearer);if(requestId!=null)h.set("PayPal-Request-Id",requestId);});
                String response=(body==null?spec:spec.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body))).retrieve().body(String.class);
                return response==null||response.isBlank()?json.createObjectNode():parse(response);
            }catch(RestClientResponseException e){
                if(e.getStatusCode().value()==401&&attempt==0){synchronized(this){appToken=null;}continue;}
                throw remote(e.getStatusCode().value());
            }catch(ResourceAccessException e){throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);}
        }throw new BillingFailure(Code.PROVIDER_NOT_CONFIGURED);
    }
    JsonNode payoutRequest(Object body,String requestId){return payoutRequest(body,requestId,true);}
    private JsonNode payoutRequest(Object body,String requestId,boolean refresh){
        configured();
        try{return parse(http.post().uri("/v1/payments/payouts").headers(h->{h.setBearerAuth(appToken());h.set("PayPal-Request-Id",requestId);})
            .contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body)).retrieve().body(String.class));
        }catch(RestClientResponseException error){
            if(error.getStatusCode().value()==401&&refresh){synchronized(this){appToken=null;}return payoutRequest(body,requestId,false);}
            if(error.getStatusCode().value()==400||error.getStatusCode().value()==422){
                var result=parse(error.getResponseBodyAsString());
                boolean duplicate="DUPLICATE_BATCH_ID".equals(result.path("name").asText());
                for(var detail:result.path("details"))if("DUPLICATE_BATCH_ID".equals(detail.path("issue").asText()))duplicate=true;
                if(duplicate){
                    var links=new java.util.ArrayList<tools.jackson.databind.JsonNode>();for(var link:result.path("links"))links.add(link);
                    for(var detail:result.path("details"))for(var link:detail.path("links"))links.add(link);
                    for(var link:links){
                        java.net.URI uri;try{uri=java.net.URI.create(link.path("href").asText());}catch(Exception ignored){continue;}
                        if("https".equals(uri.getScheme())&&java.util.Set.of("api-m.sandbox.paypal.com","api.sandbox.paypal.com").contains(uri.getHost())
                            &&uri.getUserInfo()==null&&uri.getPort()==-1&&uri.getFragment()==null&&uri.getPath().matches("/v1/payments/payouts/[A-Za-z0-9]+"))
                            return request(HttpMethod.GET,uri.getPath()+"?page_size=1000",null,null);
                    }
                    throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
                }
            }
            throw remote(error.getStatusCode().value());
        }catch(ResourceAccessException error){throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);}
    }
    public Preparation prepare(UUID authorization,UUID account,String state){
        String back=settings.fundingReturnUrl()+"?state="+encode(state);
        var source=Map.of("description","CollabPro Sandbox: autorización sin compra","usage_pattern","IMMEDIATE","usage_type","MERCHANT",
            "customer_type","CONSUMER","permit_multiple_payment_tokens",false,
            "experience_context",Map.of("shipping_preference","NO_SHIPPING","return_url",back,"cancel_url",back+"&cancelled=true"));
        var response=request(HttpMethod.POST,"/v3/vault/setup-tokens",Map.of("customer",Map.of("merchant_customer_id",account.toString()),"payment_source",Map.of("paypal",source)),
            "billing-prepare-"+authorization);
        String id=identifier(response,"id");String customer=identifier(response.path("customer"),"id");
        checkCustomer(response,account,customer);
        String status=required(response,"status");if(!Set.of("PAYER_ACTION_REQUIRED","APPROVED").contains(status))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        String approval=null;
        for(var link:response.path("links"))if("approve".equals(link.path("rel").asText()))approval=required(link,"href");
        if(approval==null||approval.length()>4000)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        URI uri;
        try{uri=URI.create(approval);}catch(IllegalArgumentException e){throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);}
        if(!"https".equals(uri.getScheme())||!Set.of("sandbox.paypal.com","www.sandbox.paypal.com").contains(uri.getHost())||
            uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()!=-1)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        return new Preparation(id,customer,approval);
    }
    private static void checkCustomer(JsonNode response,UUID account,String expected){
        var customer=response.path("customer");
        if(!expected.equals(identifier(customer,"id")))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        String merchant=customer.path("merchant_customer_id").asText("");
        if(!merchant.isEmpty()&&!merchant.equals(account.toString()))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
    }
    public Verified completeFunding(UUID authorization,UUID account,String setup,String expectedCustomer){
        if(!setup.matches("[A-Za-z0-9_-]{1,128}"))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        var approval=request(HttpMethod.GET,"/v3/vault/setup-tokens/"+setup,null,null);
        if(!setup.equals(identifier(approval,"id")))throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);checkCustomer(approval,account,expectedCustomer);
        if("PAYER_ACTION_REQUIRED".equals(approval.path("status").asText()))throw new BillingFailure(Code.AWAITING_APPROVAL,true);
        // VAULTED can be observed after a successful save whose response was lost.
        if(!Set.of("APPROVED","VAULTED").contains(approval.path("status").asText()))throw new BillingFailure(Code.PROVIDER_REJECTED);
        var saved=request(HttpMethod.POST,"/v3/vault/payment-tokens",Map.of("payment_source",Map.of("token",Map.of("id",setup,"type","SETUP_TOKEN"))),
            "billing-save-"+authorization);
        checkCustomer(saved,account,expectedCustomer);
        var paypal=saved.path("payment_source").path("paypal");
        return new Verified(identifier(saved,"id"),identifier(paypal,"payer_id"),required(paypal,"email_address"));
    }
    public String identityApproval(String state){
        configured();return "https://www.sandbox.paypal.com/connect?flowEntry=static&client_id="+encode(settings.clientId())+
            "&response_type=code&scope="+encode("openid email https://uri.paypal.com/services/paypalattributes")+
            "&redirect_uri="+encode(settings.identityReturnUrl())+"&state="+encode(state);
    }
    public Token exchangeIdentity(String code){
        var form=new LinkedMultiValueMap<String,String>();form.add("grant_type","authorization_code");form.add("code",code);
        form.add("redirect_uri",settings.identityReturnUrl());var response=oauth(form);long seconds=response.path("expires_in").asLong(0);
        if(seconds<1||seconds>86400)throw new BillingFailure(Code.INVALID_PROVIDER_RESPONSE);
        return new Token(required(response,"access_token"),clock.instant().plusSeconds(seconds));
    }
    public Verified identity(String accessToken){
        configured();
        try{var response=parse(http.get().uri("/v1/identity/openidconnect/userinfo?schema=openid").headers(h->h.setBearerAuth(accessToken)).retrieve().body(String.class));
            if(!response.path("verified_account").asBoolean(false)||!response.path("email_verified").asBoolean(false))throw new BillingFailure(Code.ACCOUNT_NOT_VERIFIED);
            String payer=identifier(response,"payer_id");return new Verified(payer,payer,required(response,"email"));
        }catch(RestClientResponseException e){throw remote(e.getStatusCode().value());}
        catch(ResourceAccessException e){throw new BillingFailure(Code.PROVIDER_UNAVAILABLE,true);}
    }
    public boolean verify(Signature signature,Map<String,Object> payload){
        if(!configuration().webhooksConfigured())throw new BillingFailure(Code.WEBHOOK_NOT_CONFIGURED);
        if(signature==null||signature.algorithm()==null||signature.certificateUrl()==null||signature.transmissionId()==null||
            signature.signature()==null||signature.transmissionTime()==null)throw new BillingFailure(Code.SIGNATURE_INVALID);
        URI cert;
        try{cert=URI.create(signature.certificateUrl());Instant.parse(signature.transmissionTime());}
        catch(Exception e){throw new BillingFailure(Code.SIGNATURE_INVALID);}
        if(!"SHA256withRSA".equals(signature.algorithm())||!"https".equals(cert.getScheme())||
            !Set.of("api.paypal.com","api.sandbox.paypal.com","api-m.sandbox.paypal.com").contains(cert.getHost())||
            cert.getUserInfo()!=null||cert.getQuery()!=null||cert.getFragment()!=null||cert.getPort()!=-1||
            !cert.getPath().matches("/v1/notifications/certs/[A-Za-z0-9_-]+")||signature.transmissionId().length()>128||signature.signature().length()>2048)
            throw new BillingFailure(Code.SIGNATURE_INVALID);
        var body=Map.of("auth_algo",signature.algorithm(),"cert_url",signature.certificateUrl(),"transmission_id",signature.transmissionId(),
            "transmission_sig",signature.signature(),"transmission_time",signature.transmissionTime(),"webhook_id",settings.webhookId(),"webhook_event",payload);
        return "SUCCESS".equals(request(HttpMethod.POST,"/v1/notifications/verify-webhook-signature",body,null).path("verification_status").asText());
    }
}
