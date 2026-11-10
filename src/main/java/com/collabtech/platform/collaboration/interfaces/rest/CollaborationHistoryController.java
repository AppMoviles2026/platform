package com.collabtech.platform.collaboration.interfaces.rest;

import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.application.ports.HistoryPorts.Entry;
import com.collabtech.platform.collaboration.domain.model.Collaboration;
import com.collabtech.platform.shared.application.pagination.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Tag(name="Collaboration history",description="Historial propio, acuerdos conservados y trazabilidad de ejecución")
@RestController @Profile("!skeleton") @RequestMapping("/api/v1")
public class CollaborationHistoryController {
    private final CollaborationHistoryService service;
    private final CurrentActor actor;
    public CollaborationHistoryController(CollaborationHistoryService service,CurrentActor actor){this.service=service;this.actor=actor;}
    @Operation(summary="Consultar el historial de colaboraciones propias",description="Incluye las participaciones registradas; permite filtrar por estado y paginar. No utiliza nombres actuales para reemplazar los del acuerdo.")
    @GetMapping("/collaborations/history")
    public PageResult<CollaborationService.CollaborationView> list(@RequestParam(required=false) Collaboration.Status status,
                                                                  @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(actor.accountId(),status,new PageRequest(page,size));
    }
    @Operation(summary="Consultar un acuerdo histórico y sus referencias autorizadas",description="Entrega condiciones conservadas y rutas reales de entregas, incidencias y cronología. Compensación/resultados se indican como no disponibles mientras sus contextos no los proporcionen.")
    @GetMapping("/collaborations/history/{id}")
    public CollaborationHistoryService.Detail detail(@PathVariable UUID id){return service.detail(actor.accountId(),id);}
    @Operation(summary="Consultar la cronología de una colaboración propia",description="Orden por secuencia persistida. Los hechos recuperados de datos anteriores están identificados como reconstructed; no se inventan actores o fechas.")
    @GetMapping("/collaborations/{id}/history")
    public PageResult<Entry> timeline(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.timeline(actor.accountId(),id,new PageRequest(page,size));
    }
}
