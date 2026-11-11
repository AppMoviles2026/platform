package com.collabtech.platform.billing.application.ports;
import com.collabtech.platform.billing.domain.model.BillingModels.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
public final class BillingPorts {
    private BillingPorts(){}
    public record Actor(UUID id,String role){}
    public interface Access {Actor actor(UUID account);}
    public interface UnitOfWork {<T>T execute(Supplier<T> work);}
    public interface Secrets {String encrypt(String plaintext);String decrypt(String ciphertext);String digest(String input);String random();String fingerprint(Object input);}
    public record Preparation(String setupId,String customerId,String approvalUrl){
        @Override public String toString(){return "ProviderPreparation[redacted]";}
    }
    public record Token(String value,Instant expiresAt){
        @Override public String toString(){return "ProviderAccess[redacted]";}
    }
    public record Verified(String reference,String payerId,String email){
        @Override public String toString(){return "ProviderVerified[redacted]";}
    }
    public record Signature(String algorithm,String certificateUrl,String transmissionId,String signature,String transmissionTime){
        @Override public String toString(){return "ProviderSignature[redacted]";}
    }
    public record Configuration(boolean enabled,boolean credentialsConfigured,boolean webhooksConfigured,String environment,
                                String fundingReturnUrl,String identityReturnUrl,String androidReturnUrl,long authorizationSeconds){}
    public interface Gateway {
        Configuration configuration();
        Preparation prepare(UUID authorization,UUID account,String state);
        String identityApproval(String state);
        Verified completeFunding(UUID authorization,UUID account,String setup,String expectedCustomer);
        Token exchangeIdentity(String code);
        Verified identity(String accessToken);
        boolean verify(Signature signature,Map<String,Object> payload);
    }
    public interface Queries {PageResult<Method> methods(UUID paymentAccount,PageRequest page);Optional<Method> active(UUID paymentAccount,Purpose purpose);}
}
