package com.collabtech.platform.campaign.interfaces.rest;

import com.collabtech.platform.campaign.application.services.CampaignPreparationService;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.interfaces.rest.resources.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.shared.application.pagination.*;
import jakarta.validation.Valid;
import java.util.UUID;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!skeleton") @RequestMapping("/api/v1/campaigns")
public class CampaignController {
    private final CampaignPreparationService service; private final CurrentActor actor;
    public CampaignController(CampaignPreparationService service, CurrentActor actor) { this.service = service; this.actor = actor; }
    @PostMapping
    public ResponseEntity<CampaignResources.Details> create(@Valid @RequestBody CampaignRequests.Create body) {
        var result = CampaignResources.details(service.create(actor.accountId(), body.title(), body.objective(), body.description(), body.category(), body.targetAudience(), body.location()));
        return ResponseEntity.created(URI.create("/api/v1/campaigns/" + result.id())).body(result);
    }
    @PutMapping("/{id}/conditions")
    public CampaignResources.Details conditions(@PathVariable UUID id, @Valid @RequestBody CampaignRequests.Conditions body) {
        return CampaignResources.details(service.define(actor.accountId(), new CampaignId(id), body.requirements().stream().map(CampaignRequests.Requirement::view).toList(),
                body.deliverables().stream().map(CampaignRequests.Deliverable::view).toList(), body.applicationDeadline(), body.compensation().value()));
    }
    @PostMapping("/{id}/publication")
    public CampaignResources.Details publish(@PathVariable UUID id) { return CampaignResources.details(service.publish(actor.accountId(), new CampaignId(id))); }
    @GetMapping("/{id}")
    public CampaignResources.Details own(@PathVariable UUID id) { return CampaignResources.details(service.own(actor.accountId(), new CampaignId(id))); }
    @GetMapping("/mine")
    public PageResult<CampaignResources.Summary> mine(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return CampaignResources.page(service.mine(actor.accountId(), new PageRequest(page, size)));
    }
    @GetMapping("/published")
    public PageResult<CampaignResources.Summary> published(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return CampaignResources.page(service.published(actor.accountId(), new PageRequest(page, size)));
    }
}
