package com.collabtech.platform.campaign.interfaces.rest;
import com.collabtech.platform.campaign.application.services.ApplicationManagementService;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.shared.application.pagination.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!skeleton") @RequestMapping("/api/v1")
public class ApplicationController {
    private final ApplicationManagementService service; private final CurrentActor actor;
    public ApplicationController(ApplicationManagementService service,CurrentActor actor) { this.service=service; this.actor=actor; }
    public record Submit(@NotBlank @Size(max=4000) String message,@Size(max=50) Set<@NotNull UUID> confirmedRequirementIds) {
        public Submit { confirmedRequirementIds=confirmedRequirementIds==null ? Set.of():Set.copyOf(confirmedRequirementIds); }
    }
    public record Update(@NotBlank @Size(max=4000) String message,@PositiveOrZero Long expectedVersion) {}
    public record Cancel(@PositiveOrZero Long expectedVersion) {}
    @PostMapping("/campaigns/{id}/applications")
    public ResponseEntity<CampaignViews.ApplicationView> submit(@PathVariable UUID id,@Valid @RequestBody Submit body,
            @RequestHeader(name="Idempotency-Key",required=false) String key) {
        var result=service.submit(actor.accountId(),new CampaignId(id),body.message(),body.confirmedRequirementIds(),key);
        return ResponseEntity.created(URI.create("/api/v1/applications/"+result.id())).body(result);
    }
    @GetMapping("/applications/mine")
    public PageResult<CampaignViews.ApplicationView> mine(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.mine(actor.accountId(),new PageRequest(page,size)); }
    @GetMapping("/applications/{id}")
    public CampaignViews.ApplicationView get(@PathVariable UUID id) { return service.get(actor.accountId(),new ApplicationId(id)); }
    @PutMapping("/applications/{id}")
    public CampaignViews.ApplicationView update(@PathVariable UUID id,@Valid @RequestBody Update body) { return service.update(actor.accountId(),new ApplicationId(id),body.message(),body.expectedVersion()); }
    @PostMapping("/applications/{id}/cancellation")
    public CampaignViews.ApplicationView cancel(@PathVariable UUID id,@Valid @RequestBody(required=false) Cancel body) { return service.cancel(actor.accountId(),new ApplicationId(id),body==null?null:body.expectedVersion()); }
}
