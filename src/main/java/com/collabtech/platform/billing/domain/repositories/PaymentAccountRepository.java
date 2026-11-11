package com.collabtech.platform.billing.domain.repositories;
import com.collabtech.platform.billing.domain.model.BillingModels.*;
import java.time.Instant;
import java.util.*;
public interface PaymentAccountRepository {
    Account ensure(UUID account,String role,Instant now);
    Optional<Account> account(UUID account,boolean lock);
    Optional<Account> byId(UUID paymentAccount,boolean lock);
    Optional<Authorization> authorization(UUID id);
    Optional<Authorization> request(UUID paymentAccount,UUID request);
    Optional<Authorization> state(String hash);
    void create(Authorization authorization);
    void save(Authorization authorization);
    Optional<Method> method(UUID id);
    Optional<Method> reference(String hash);
    Optional<Method> subject(Purpose purpose,String subjectHash);
    void save(Method method);
    boolean resource(String hash,boolean lock,Instant now);
    void revokeResource(String hash,Instant now);
    Optional<Event> event(String id);
    boolean reserve(Event event);
    void event(Event event);
    void audit(UUID paymentAccount,UUID authorization,UUID method,String type,Instant now);
}
