package com.collabtech.platform.collaboration.interfaces.rest;
import com.collabtech.platform.collaboration.application.services.CollaborationService;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.shared.application.pagination.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;

@Tag(name="Collaboration", description="Acuerdos bilaterales, activación y consulta autorizada del ciclo de vida")
@RestController @Profile("!skeleton") @RequestMapping("/api/v1")
public class CollaborationController {
    private final CollaborationService service;private final CurrentActor actor;
    public CollaborationController(CollaborationService service,CurrentActor actor){this.service=service;this.actor=actor;}
    public record Proposal(@NotNull UUID applicationId){}
    public record AgreementDecision(@NotNull UUID termsVersion,@NotNull Agreement.Decision decision){}
    public record Activation(@NotNull UUID agreementId){}
    @Operation(summary="Proponer condiciones desde una postulación seleccionada", description="No registra aceptaciones. Un reintento recupera el mismo acuerdo. El plazo termina al llegar la primera entrega acordada.")
    @ApiResponse(responseCode="201", description="Acuerdo propuesto o recuperado; Location identifica el recurso")
    @PostMapping("/agreements")
    public ResponseEntity<CollaborationService.AgreementView> propose(@Valid @RequestBody Proposal body){var result=service.propose(actor.accountId(),body.applicationId());return ResponseEntity.created(URI.create("/api/v1/agreements/"+result.id())).body(result);}
    @GetMapping("/agreements/{id}")
    public CollaborationService.AgreementView agreement(@PathVariable UUID id){return service.agreement(actor.accountId(),id);}
    @GetMapping("/agreements/mine")
    public PageResult<CollaborationService.AgreementView> agreements(@RequestParam(required=false) Agreement.Status status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.agreements(actor.accountId(),status,new PageRequest(page,size));}
    @Operation(summary="Aceptar o rechazar las condiciones con la sesión actual", description="Enviar snapshot.termsVersion. La segunda aceptación activa una sola colaboración. El rechazo o vencimiento impiden su inicio.")
    @PostMapping("/agreements/{id}/decisions")
    public CollaborationService.AgreementView decide(@PathVariable UUID id,@Valid @RequestBody AgreementDecision body){return service.decide(actor.accountId(),id,body.termsVersion(),body.decision());}
    @Operation(summary="Recuperar la activación de un acuerdo aceptado por ambas partes", description="La segunda firma activa la colaboración automáticamente; este POST idempotente devuelve esa misma colaboración y exige ambas aceptaciones.")
    @ApiResponse(responseCode="201", description="Colaboración activada o recuperada; Location identifica el recurso")
    @PostMapping("/collaborations")
    public ResponseEntity<CollaborationService.CollaborationView> create(@Valid @RequestBody Activation body){var result=service.create(actor.accountId(),body.agreementId());return ResponseEntity.created(URI.create("/api/v1/collaborations/"+result.id())).body(result);}
    @Operation(summary="Consultar estado, condiciones y obligaciones propias", description="Refleja vencimientos con hora del servidor aunque aún no se haya ejecutado el proceso periódico. pendingActions describe tareas; availableActions identifica comandos implementados.")
    @GetMapping("/collaborations/{id}")
    public CollaborationService.CollaborationView collaboration(@PathVariable UUID id){return service.collaboration(actor.accountId(),id);}
    @GetMapping("/collaborations/mine")
    public PageResult<CollaborationService.CollaborationView> collaborations(@RequestParam(required=false) Collaboration.Status status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.collaborations(actor.accountId(),status,new PageRequest(page,size));}
}
