package com.collabtech.platform.billing.infrastructure.paypal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
@ConfigurationProperties("billing.paypal")
public record PayPalProperties(boolean enabled,String clientId,String clientSecret,String webhookId,String fundingReturnUrl,
                               String identityReturnUrl,String androidReturnUrl,long authorizationSeconds) {
    public PayPalProperties {
        if(authorizationSeconds<60||authorizationSeconds>3600)throw new IllegalArgumentException("Billing authorization lifetime must be 60..3600 seconds");
        validate(fundingReturnUrl);validate(identityReturnUrl);
        if(!"collabpro://billing-return".equals(androidReturnUrl))throw new IllegalArgumentException("Unapproved billing app return URL");
    }
    private static void validate(String value){var uri=URI.create(value);
        if(!uri.isAbsolute()||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null||
            !java.util.Set.of("https","http").contains(uri.getScheme()))throw new IllegalArgumentException("Invalid configured PayPal return URL");}
    public boolean credentials(){return clientId!=null&&!clientId.isBlank()&&clientSecret!=null&&!clientSecret.isBlank();}
    @Override public String toString(){return "PayPalProperties[environment=SANDBOX,credentials=redacted]";}
}
