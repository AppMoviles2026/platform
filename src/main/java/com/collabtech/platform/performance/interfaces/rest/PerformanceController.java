package com.collabtech.platform.performance.interfaces.rest;

import com.collabtech.platform.performance.application.services.PerformanceService;
import com.collabtech.platform.performance.domain.model.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.shared.application.pagination.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name="Performance",description="Métricas autorizadas con periodo y evidencias manuales diferenciadas")
@RestController @Profile("!skeleton") @RequestMapping("/api/v1")
public class PerformanceController {
    private final PerformanceService service;private final CurrentActor actor;
    public PerformanceController(PerformanceService service,CurrentActor actor){this.service=service;this.actor=actor;}
    public record RefreshBody(@NotNull UUID requestId,@NotNull UUID socialId,@NotNull UUID submissionId,@NotBlank @Size(max=128) String postId){}
    public record EvidenceBody(@NotNull UUID requestId,UUID submissionId,@NotBlank @Size(max=4000) String description,
                               @NotEmpty @Size(max=10) List<@Valid @NotNull Reference> references,
                               Map<MetricType,BigDecimal> claimedMetrics,@NotNull Instant observedAt,@NotNull Period period){}
    public record ReviewBody(@NotNull UUID requestId,@NotNull @Min(0) Long expectedVersion,@NotNull PerformanceEvidence.Status decision,@Size(max=2000) String observations){}
    @Operation(summary="Consultar métricas de la colaboración",description="Solo participantes. Datos automáticos de publicaciones actuales, fuente y periodo LIFETIME; sin sumar capturas acumuladas ni convertir evidencia manual en métricas automáticas. 200 con NO_DATA cuando no hay datos.")
    @GetMapping("/collaborations/{id}/metrics")
    public PerformanceService.Report report(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {
        return service.report(actor.accountId(),id,new PageRequest(page,size));
    }
    @Operation(summary="Actualizar métricas autorizadas de una publicación",description="requestId permite reintentar sin duplicar la consulta. TikTok exige video.list y un enlace canónico al video de la versión de entrega indicada. Las limitaciones del proveedor devuelven estado explícito y no generan ceros.")
    @PostMapping("/collaborations/{id}/metrics/refresh")
    public PerformanceService.RefreshResult refresh(@PathVariable UUID id,@Valid @RequestBody RefreshBody body) {
        return service.refresh(actor.accountId(),id,new PerformanceService.RefreshInput(body.requestId(),body.socialId(),body.submissionId(),body.postId()));
    }
    @GetMapping("/collaborations/{id}/metrics/snapshots")
    public PageResult<PerformanceService.AutomaticSnapshot> snapshots(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {
        return service.snapshots(actor.accountId(),id,new PageRequest(page,size));
    }
    @Operation(summary="Registrar evidencia manual del creador",description="Los valores son declarados por el creador y no se agregan a los datos automáticos. Requiere periodo, fecha de observación y referencias válidas.")
    @ApiResponse(responseCode="201",description="Evidencia registrada o recuperada")
    @PostMapping("/collaborations/{id}/evidence")
    public ResponseEntity<PerformanceService.EvidenceResult> register(@PathVariable UUID id,@Valid @RequestBody EvidenceBody body) {
        var result=service.register(actor.accountId(),id,new PerformanceService.EvidenceInput(body.requestId(),body.submissionId(),body.description(),
                body.references(),counts(body.claimedMetrics()),body.observedAt(),body.period()));
        return ResponseEntity.created(URI.create("/api/v1/performance-evidence/"+result.evidence().id())).body(result);
    }
    @GetMapping("/collaborations/{id}/evidence")
    public PageResult<PerformanceService.EvidenceView> evidence(@PathVariable UUID id,@RequestParam(required=false)PerformanceEvidence.Status status,
                                                              @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {
        return service.evidence(actor.accountId(),id,status,new PageRequest(page,size));
    }
    @GetMapping("/performance-evidence/{id}")
    public PerformanceService.EvidenceView evidence(@PathVariable UUID id){return service.evidence(actor.accountId(),id);}
    @Operation(summary="Registrar revisión empresarial de una evidencia",description="VERIFIED significa revisada manualmente por la empresa; el origen sigue siendo MANUAL. REJECTED requiere observaciones.")
    @PostMapping("/performance-evidence/{id}/verification")
    public PerformanceService.EvidenceResult review(@PathVariable UUID id,@Valid @RequestBody ReviewBody body) {
        return service.review(actor.accountId(),id,new PerformanceService.ReviewInput(body.requestId(),body.expectedVersion(),body.decision(),body.observations()));
    }
    @ApiResponse(responseCode="201",description="Archivo privado guardado o recuperado")
    @PostMapping(value="/collaborations/{id}/evidence-assets",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PerformanceService.AssetView> upload(@PathVariable UUID id,@RequestHeader("Idempotency-Key")UUID request,@RequestPart("file")MultipartFile file)throws IOException {
        var result=service.upload(actor.accountId(),id,request,file.getOriginalFilename(),file.getContentType(),file.getBytes());
        return ResponseEntity.created(URI.create("/api/v1/performance-assets/"+result.asset().id())).body(result);
    }
    @GetMapping("/performance-assets/{id}")
    public PerformanceService.AssetView asset(@PathVariable UUID id){return service.asset(actor.accountId(),id);}
    @ApiResponse(responseCode="200",description="Archivo privado en su formato original",content={
            @Content(mediaType="application/pdf",schema=@Schema(type="string",format="binary")),
            @Content(mediaType="image/png",schema=@Schema(type="string",format="binary")),
            @Content(mediaType="image/jpeg",schema=@Schema(type="string",format="binary")),
            @Content(mediaType="image/webp",schema=@Schema(type="string",format="binary")),
            @Content(mediaType="video/mp4",schema=@Schema(type="string",format="binary"))})
    @GetMapping("/performance-assets/{id}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        var result=service.download(actor.accountId(),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.asset().mediaType())).contentLength(result.asset().byteSize())
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.asset().filename(),StandardCharsets.UTF_8).build().toString()).body(result.bytes());
    }
    private Map<MetricType,Long> counts(Map<MetricType,BigDecimal> values) {
        var result=new EnumMap<MetricType,Long>(MetricType.class);
        if(values!=null)values.forEach((key,value)->{if(value==null||value.signum()<0)throw new IllegalArgumentException("Metric counts must be nonnegative");
            try{result.put(key,value.longValueExact());}catch(ArithmeticException error){throw new IllegalArgumentException("Metric counts must fit a whole number");}});
        return result;
    }
}
