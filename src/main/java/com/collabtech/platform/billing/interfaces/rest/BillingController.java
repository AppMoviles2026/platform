package com.collabtech.platform.billing.interfaces.rest;

import com.collabtech.platform.billing.application.services.BillingService;
import com.collabtech.platform.billing.application.services.BillingService.*;
import com.collabtech.platform.billing.domain.model.BillingModels.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.shared.application.pagination.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

@RestController @Profile("!skeleton") @RequestMapping("/api/v1") @Tag(name="Billing · PayPal Sandbox")
public class BillingController {
    public record BeginRequest(@NotNull UUID requestId,@NotNull Client client){}
    public record ValidationRequest(@NotNull UUID methodId){}
    private final BillingService service;private final CurrentActor actor;
    public BillingController(BillingService service,CurrentActor actor){this.service=service;this.actor=actor;}
    @PostMapping("/user/{id}/billing") @ResponseStatus(HttpStatus.ACCEPTED) @Operation(summary="Iniciar o reintentar autorización financiera Sandbox sin compra")
    public ResponseEntity<AuthorizationView> begin(@PathVariable UUID id,@Valid @RequestBody BeginRequest body){
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(service.begin(actor.accountId(),id,body.requestId(),body.client()));
    }
    @GetMapping("/user/{id}/billing") @Operation(summary="Consultar métodos propios reales; PAYMENT para empresa y PAYOUT para creador")
    public PageResult<MethodView> methods(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return service.methods(actor.accountId(),id,new PageRequest(page,size));
    }
    @GetMapping("/billing/authorizations/{id}") public ResponseEntity<AuthorizationView> authorization(@PathVariable UUID id){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.authorization(actor.accountId(),id));
    }
    @PostMapping("/billing/authorizations/{id}/cancellation") public AuthorizationView cancel(@PathVariable UUID id){
        return service.cancel(actor.accountId(),id);
    }
    @PostMapping("/user/{id}/billing/validation") @Operation(summary="Comprobar requisito financiero sin efectuar un pago")
    public Readiness validate(@PathVariable UUID id,@Valid @RequestBody ValidationRequest body){
        return service.requireUsable(actor.accountId(),id,body.methodId());
    }
}
