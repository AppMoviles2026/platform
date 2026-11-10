package com.collabtech.platform.performance.interfaces.rest;
import com.collabtech.platform.performance.application.services.AttributionService;
import com.collabtech.platform.performance.application.ports.AttributionPorts.Destination;
import com.collabtech.platform.performance.domain.model.AttributionModels.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.shared.application.pagination.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!skeleton") @RequestMapping("/api/v1") @Tag(name="Attribution",description="Interacciones atribuibles y estimaciones diferenciadas de ventas verificadas")
public class AttributionController {
    private final AttributionService service;private final CurrentActor actor;
    public AttributionController(AttributionService service,CurrentActor actor){this.service=service;this.actor=actor;}
    public record AttributionCreateBody(@NotNull UUID requestId,@NotNull Kind kind,@NotBlank @Size(max=120) String label,@Size(max=40) String destinationId){}
    public record AttributionStatusBody(@NotNull UUID requestId,@NotNull @Min(0) Long expectedVersion,@NotNull Status status){}
    public record AttributionInteractionBody(@NotNull UUID requestId,@NotNull UUID eventId,@NotNull InteractionType type,@NotNull Instant occurredAt,@NotNull UUID evidenceId){}
    @GetMapping("/attribution/destinations")
    @Operation(summary="Consultar los destinos aprobados por configuración",description="El cliente selecciona un ID del servidor; nunca envía una URL de redirección.")
    public List<Destination> destinations(){return service.destinations(actor.accountId());}
    @PostMapping("/collaborations/{id}/attribution") @ApiResponse(responseCode="201",description="Mecanismo creado o recuperado")
    @Operation(summary="Crear un enlace o código para una colaboración",description="Solo la empresa participante. LINK requiere destinationId configurado; CODE genera un código sin destino. requestId protege los reintentos.")
    public ResponseEntity<AttributionService.MechanismResult> create(@PathVariable UUID id,@Valid @RequestBody AttributionCreateBody body){
        var result=service.create(actor.accountId(),id,new AttributionService.Create(body.requestId(),body.kind(),body.label(),body.destinationId()));
        return ResponseEntity.created(URI.create("/api/v1/collaborations/"+id+"/attribution")).body(result);
    }
    @GetMapping("/collaborations/{id}/attribution")
    @Operation(summary="Consultar resultados atribuibles con fuente, periodo y advertencia",description="Solo participantes. start y end opcionales, ambos juntos, intervalo [start,end). Totales globales independientes de paginación; demostraciones separadas. Los resultados son estimaciones, no ventas verificadas.")
    public AttributionService.Results results(@PathVariable UUID id,@RequestParam(required=false)Instant start,@RequestParam(required=false)Instant end,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return service.results(actor.accountId(),id,start,end,new PageRequest(page,size));
    }
    @PostMapping("/attribution/{id}/status")
    @Operation(summary="Activar o desactivar el mecanismo",description="Solo la empresa participante; expectedVersion evita sobrescribir una edición antigua.")
    public AttributionService.MechanismResult status(@PathVariable UUID id,@Valid @RequestBody AttributionStatusBody body){
        return service.status(actor.accountId(),id,new AttributionService.ChangeStatus(body.requestId(),body.expectedVersion(),body.status()));
    }
    @PostMapping("/attribution/{id}/interactions") @ApiResponse(responseCode="201",description="Declaración registrada o recuperada")
    @Operation(summary="Declarar una consulta o uso de código con evidencia",description="Solo la empresa participante. eventId identifica el hecho de negocio y debe reutilizarse aunque cambie requestId. No admite CLICK ni ventas verificadas. Una evidencia rechazada queda excluida de los totales.")
    public ResponseEntity<AttributionService.InteractionResult> record(@PathVariable UUID id,@Valid @RequestBody AttributionInteractionBody body){
        return ResponseEntity.status(201).body(service.record(actor.accountId(),id,new AttributionService.RecordInteraction(
                body.requestId(),body.eventId(),body.type(),body.occurredAt(),body.evidenceId())));
    }
    @GetMapping("/attribution/{id}/interactions")
    @Operation(summary="Consultar el registro privado de interacciones",description="No expone identificadores de visitantes, hashes de deduplicación, IP ni user-agent.")
    public PageResult<AttributionService.InteractionView> interactions(@PathVariable UUID id,@RequestParam(required=false)Instant start,@RequestParam(required=false)Instant end,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return service.interactions(actor.accountId(),id,start,end,new PageRequest(page,size));
    }
}
