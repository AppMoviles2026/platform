package com.collabtech.platform.billing.interfaces.rest;
import com.collabtech.platform.billing.application.services.SubscriptionService;
import com.collabtech.platform.billing.application.services.SubscriptionService.*;
import com.collabtech.platform.billing.domain.model.BillingModels.Client;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.shared.application.pagination.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @Profile("!skeleton") @RequestMapping("/api/v1") @Tag(name="Company subscriptions · PayPal Sandbox")
public class SubscriptionController {
    public record SubscriptionRequest(@NotNull UUID requestId,@NotNull UUID planId,@NotNull UUID methodId,@NotNull Client client){}
    public record ResumeRequest(@NotNull @jakarta.validation.constraints.Min(0) Long expectedVersion){}
    private final SubscriptionService service;private final CurrentActor actor;
    public SubscriptionController(SubscriptionService service,CurrentActor actor){this.service=service;this.actor=actor;}
    @GetMapping("/subscription-plans") public List<PlanView> plans(){return service.plans(actor.accountId());}
    @PostMapping("/user/{id}/subscription") @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<View> create(@PathVariable UUID id,@Valid @RequestBody SubscriptionRequest body){
        var value=service.begin(actor.accountId(),id,body.requestId(),body.planId(),body.methodId(),body.client());
        return ResponseEntity.created(java.net.URI.create("/api/v1/subscriptions/"+value.subscriptionId())).cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/user/{id}/subscription") public View current(@PathVariable UUID id){return service.current(actor.accountId(),id);}
    @GetMapping("/user/{id}/subscriptions") public PageResult<View> history(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.history(actor.accountId(),id,new PageRequest(page,size));}
    @GetMapping("/subscriptions/{id}") public View detail(@PathVariable UUID id){return service.detail(actor.accountId(),id,true);}
    @PostMapping("/subscriptions/{id}/refresh") public View refresh(@PathVariable UUID id){return service.detail(actor.accountId(),id,true);}
    @PostMapping("/subscriptions/{id}/resume-approval") public View resume(@PathVariable UUID id,@Valid @RequestBody ResumeRequest body){return service.resumeApproval(actor.accountId(),id,body.expectedVersion());}
    @PostMapping("/subscriptions/{id}/cancellation") public View cancel(@PathVariable UUID id){return service.cancel(actor.accountId(),id);}
}
