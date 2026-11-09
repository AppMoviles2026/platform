package com.collabtech.platform.collaboration.interfaces.rest;

import com.collabtech.platform.collaboration.application.services.DeliveryService;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.shared.application.pagination.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name="Collaboration deliveries",description="Entregas, archivos privados, revisiones y correcciones versionadas")
@RestController
@Profile("!skeleton")
@RequestMapping("/api/v1")
public class DeliveryController {
    private final DeliveryService service;
    private final CurrentActor actor;
    public DeliveryController(DeliveryService service, CurrentActor actor) { this.service=service; this.actor=actor; }

    public record SubmissionBody(@NotNull UUID requestId, @NotNull UUID obligationId, @NotNull DeliveryContent content) {}
    public record CorrectionBody(@NotNull UUID requestId, @NotNull @Min(0) Long expectedVersion, @NotNull DeliveryContent content) {}
    public record ApprovalBody(@NotNull UUID requestId, @NotNull UUID submissionId,
                               @NotNull @Min(0) Long expectedVersion, @Size(max=4000) String observations) {}
    public record RejectionBody(@NotNull UUID requestId, @NotNull UUID submissionId, @NotNull @Min(0) Long expectedVersion,
                                @NotBlank @Size(max=4000) String observations, @NotNull Instant correctionDeadline) {}

    @Operation(summary="Registrar una pieza del contenido acordado",description="Solo el creador. requestId identifica reintentos; el servidor asigna un cupo de la obligación. Admite entregas tardías y exige contenido y evidencia.")
    @ApiResponse(responseCode="201",description="Entrega registrada o recuperada")
    @PostMapping("/collaborations/{id}/deliverables")
    public ResponseEntity<DeliveryService.Result> submit(@PathVariable UUID id, @Valid @RequestBody SubmissionBody body) {
        var result=service.submit(actor.accountId(),id,new DeliveryService.Submit(body.requestId(),body.obligationId(),body.content()));
        return ResponseEntity.created(URI.create("/api/v1/deliverables/"+result.deliverable().id())).body(result);
    }
    @GetMapping("/collaborations/{id}/deliverables")
    public PageResult<DeliveryService.View> list(@PathVariable UUID id, @RequestParam(required=false) Deliverable.Status status,
                                               @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.list(actor.accountId(),id,status,new PageRequest(page,size));
    }
    @GetMapping("/deliverables/{id}")
    public DeliveryService.View get(@PathVariable UUID id) { return service.get(actor.accountId(),id); }

    @Operation(summary="Consultar las versiones y decisiones históricas de una entrega")
    @GetMapping("/deliverables/{id}/versions")
    public PageResult<Deliverable.Revision> versions(@PathVariable UUID id, @RequestParam(defaultValue="0") int page,
                                                    @RequestParam(defaultValue="20") int size) {
        return service.revisions(actor.accountId(),id,new PageRequest(page,size));
    }
    @Operation(summary="Presentar una corrección tras un rechazo",description="Requiere expectedVersion actual y una fecha de corrección aún vigente. Conserva la entrega y revisión anteriores.")
    @ApiResponse(responseCode="201",description="Nueva versión registrada o recuperada")
    @PostMapping("/deliverables/{id}/versions")
    public ResponseEntity<DeliveryService.Result> correct(@PathVariable UUID id, @Valid @RequestBody CorrectionBody body) {
        var result=service.correct(actor.accountId(),id,new DeliveryService.Correction(body.requestId(),body.expectedVersion(),body.content()));
        return ResponseEntity.created(URI.create("/api/v1/deliverables/"+id+"/versions")).body(result);
    }

    @Operation(summary="Aprobar la versión exacta pendiente de revisión",description="Solo la marca participante. Requiere submissionId y expectedVersion. Una aprobación no registra ni confirma un pago.")
    @PostMapping("/deliverables/{id}/validation")
    public DeliveryService.Result approve(@PathVariable UUID id, @Valid @RequestBody ApprovalBody body) {
        return service.validate(actor.accountId(),id,new DeliveryService.Validation(body.requestId(),body.submissionId(),
                body.expectedVersion(),body.observations(),null),Deliverable.Decision.APPROVED);
    }
    @Operation(summary="Rechazar la versión pendiente y fijar un plazo de corrección",description="Solo la marca participante. Observaciones y correctionDeadline futura son obligatorias.")
    @PostMapping("/deliverables/{id}/rejection")
    public DeliveryService.Result reject(@PathVariable UUID id, @Valid @RequestBody RejectionBody body) {
        return service.validate(actor.accountId(),id,new DeliveryService.Validation(body.requestId(),body.submissionId(),
                body.expectedVersion(),body.observations(),body.correctionDeadline()),Deliverable.Decision.REJECTED);
    }

    @Operation(summary="Subir un archivo privado de contenido o evidencia",description="Multipart con campo file e Idempotency-Key UUID. PDF, PNG, JPEG, WebP o MP4; máximo 10 MiB por defecto. Referenciar su asset.id al presentar una entrega.")
    @ApiResponse(responseCode="201",description="Archivo guardado o recuperado")
    @PostMapping(value="/collaborations/{id}/assets",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeliveryService.AssetView> upload(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID request,
                                                          @RequestPart("file") MultipartFile file) throws IOException {
        var result=service.upload(actor.accountId(),id,request,file.getOriginalFilename(),file.getContentType(),file.getBytes());
        return ResponseEntity.created(URI.create("/api/v1/collaboration-assets/"+result.asset().id())).body(result);
    }
    @GetMapping("/collaboration-assets/{id}")
    public DeliveryService.AssetView asset(@PathVariable UUID id) { return service.asset(actor.accountId(),id); }

    @Operation(summary="Descargar un archivo con autorización",description="El creador ve sus archivos; la marca solo después de que se incluyan en una versión de entrega. Requiere JWT; no se publica una URL anónima.")
    @ApiResponse(responseCode="200",description="Archivo binario privado en su formato original",content={
            @Content(mediaType="application/pdf",schema=@Schema(type="string",format="binary")),
            @Content(mediaType="image/png",schema=@Schema(type="string",format="binary")),
            @Content(mediaType="image/jpeg",schema=@Schema(type="string",format="binary")),
            @Content(mediaType="image/webp",schema=@Schema(type="string",format="binary")),
            @Content(mediaType="video/mp4",schema=@Schema(type="string",format="binary"))})
    @GetMapping("/collaboration-assets/{id}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        var result=service.download(actor.accountId(),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.asset().mediaType()))
                .contentLength(result.asset().byteSize()).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(result.asset().filename(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff")
                .header("Content-Security-Policy","default-src 'none'")
                .body(result.bytes());
    }
}
