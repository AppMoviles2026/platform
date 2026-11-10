package com.collabtech.platform.collaboration.interfaces.rest;

import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.shared.application.pagination.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name="Collaboration incidents",description="Incidencias de participantes y resoluciones confirmadas por la contraparte")
@RestController @Profile("!skeleton") @RequestMapping("/api/v1")
public class IncidentController {
    private final IncidentService service;
    private final CurrentActor actor;
    public IncidentController(IncidentService service,CurrentActor actor){this.service=service;this.actor=actor;}

    public record OpenBody(@NotNull UUID requestId,@NotNull Incident.Category category,@NotBlank @Size(max=4000) String description,
                           UUID deliverableId,@Size(max=10) List<DeliveryContent.Reference> evidence) {}
    public record ProposalBody(@NotNull UUID requestId,@NotNull @Min(0) Long expectedVersion,@NotNull Incident.Terms terms) {}
    public record DecisionBody(@NotNull UUID requestId,@NotNull @Min(0) Long expectedVersion,
                               @NotNull Incident.Decision decision,@Size(max=2000) String reason) {}

    @Operation(summary="Abrir una incidencia como participante",description="Disponible durante la ejecución y hasta siete días desde la finalización/cancelación registrada. Conserva un bloqueo independiente de las otras incidencias.")
    @ApiResponse(responseCode="201",description="Incidencia registrada o recuperada por requestId")
    @PostMapping("/collaborations/{id}/incidents")
    public ResponseEntity<IncidentService.Result> open(@PathVariable UUID id,@Valid @RequestBody OpenBody body) {
        var result=service.open(actor.accountId(),id,new IncidentService.Open(body.requestId(),body.category(),body.description(),body.deliverableId(),body.evidence()));
        return ResponseEntity.created(URI.create("/api/v1/incidents/"+result.incident().id())).body(result);
    }
    @GetMapping("/collaborations/{id}/incidents")
    public PageResult<IncidentService.View> list(@PathVariable UUID id,@RequestParam(required=false) Incident.Status status,
                                                @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(actor.accountId(),id,status,new PageRequest(page,size));
    }
    @GetMapping("/incidents/{id}")
    public IncidentService.View get(@PathVariable UUID id){return service.get(actor.accountId(),id);}

    @Operation(summary="Proponer una resolución con condiciones inmutables",description="Puede mantener el acuerdo, extender el plazo de una versión rechazada o cancelar una colaboración no completada. La otra parte debe aceptar.")
    @ApiResponse(responseCode="201",description="Propuesta registrada o recuperada")
    @PostMapping("/incidents/{id}/resolutions")
    public ResponseEntity<IncidentService.Result> propose(@PathVariable UUID id,@Valid @RequestBody ProposalBody body) {
        var result=service.propose(actor.accountId(),id,new IncidentService.Propose(body.requestId(),body.expectedVersion(),body.terms()));
        return ResponseEntity.created(URI.create("/api/v1/incidents/"+id+"/resolutions")).body(result);
    }
    @GetMapping("/incidents/{id}/resolutions")
    public PageResult<Incident.Proposal> proposals(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,
                                                  @RequestParam(defaultValue="20") int size) {
        return service.proposals(actor.accountId(),id,new PageRequest(page,size));
    }
    @Operation(summary="Aceptar, rechazar o retirar una propuesta exacta",description="ACCEPT/REJECT corresponden a la contraparte; WITHDRAW al proponente. Rechazar requiere motivo. Una resolución no cierra otras incidencias ni confirma pagos.")
    @PostMapping("/incidents/{id}/resolutions/{resolutionId}/decisions")
    public IncidentService.Result decide(@PathVariable UUID id,@PathVariable UUID resolutionId,@Valid @RequestBody DecisionBody body) {
        return service.decide(actor.accountId(),id,resolutionId,new IncidentService.Decide(body.requestId(),body.expectedVersion(),body.decision(),body.reason()));
    }
    @Operation(summary="Subir evidencia privada de una incidencia",description="Ambos participantes pueden subir PDF, PNG, JPEG, WebP o MP4. Requiere Idempotency-Key UUID y campo multipart file; la contraparte accede cuando se adjunta a una incidencia/propuesta.")
    @ApiResponse(responseCode="201",description="Archivo guardado o recuperado")
    @PostMapping(value="/collaborations/{id}/incident-assets",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeliveryService.AssetView> upload(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID request,
                                                          @RequestPart("file") MultipartFile file)throws IOException {
        var result=service.upload(actor.accountId(),id,request,file.getOriginalFilename(),file.getContentType(),file.getBytes());
        return ResponseEntity.created(URI.create("/api/v1/collaboration-assets/"+result.asset().id())).body(result);
    }
}
