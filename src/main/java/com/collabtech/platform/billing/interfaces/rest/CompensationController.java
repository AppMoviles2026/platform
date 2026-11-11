package com.collabtech.platform.billing.interfaces.rest;
import com.collabtech.platform.billing.application.services.CompensationService;
import com.collabtech.platform.billing.domain.model.CompensationModels.Audit;
import com.collabtech.platform.billing.domain.model.BillingModels.Client;
import com.collabtech.platform.shared.application.pagination.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @Profile("!skeleton") @RequestMapping("/api/v1") @Tag(name="Compensation · Sandbox funding and payouts")
public class CompensationController {
    public record PaymentRequest(@NotNull UUID requestId,UUID methodId,@NotNull Client client,@NotNull @Min(0) Long expectedVersion){}
    public record ConfirmationRequest(@NotNull UUID requestId,@NotNull @Min(0) Long expectedVersion){}
    private final CompensationService service;private final CurrentActor actor;
    public CompensationController(CompensationService service,CurrentActor actor){this.service=service;this.actor=actor;}
    @GetMapping("/collaborations/{id}/compensation") public CompensationService.View read(@PathVariable UUID id){return service.read(actor.accountId(),id,true);}
    @GetMapping("/compensations/{id}") public CompensationService.View byId(@PathVariable UUID id){
        return service.readResource(actor.accountId(),id);}
    @PostMapping("/collaborations/{id}/payment") @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<CompensationService.View> start(@PathVariable UUID id,@Valid @RequestBody PaymentRequest body){
        var result=service.start(actor.accountId(),id,body.requestId(),body.methodId(),body.client(),body.expectedVersion());
        return ResponseEntity.created(java.net.URI.create("/api/v1/compensations/"+result.compensationId())).cacheControl(CacheControl.noStore()).body(result);}
    @PostMapping("/collaborations/{id}/payment/confirmation") public CompensationService.View confirm(@PathVariable UUID id,@Valid @RequestBody ConfirmationRequest body){
        return service.confirm(actor.accountId(),id,body.requestId(),body.expectedVersion());}
    @PostMapping("/collaborations/{id}/payment/refresh") public CompensationService.View refresh(@PathVariable UUID id){return service.read(actor.accountId(),id,true);}
    @GetMapping("/collaborations/{id}/compensation/history") public PageResult<Audit> history(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return service.history(actor.accountId(),id,new PageRequest(page,size));}
}
