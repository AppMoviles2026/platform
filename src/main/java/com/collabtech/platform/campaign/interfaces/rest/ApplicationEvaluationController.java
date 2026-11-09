package com.collabtech.platform.campaign.interfaces.rest;
import com.collabtech.platform.campaign.application.services.ApplicationEvaluationService;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.shared.application.pagination.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!skeleton") @RequestMapping("/api/v1/campaigns/{id}/applications")
public class ApplicationEvaluationController {
    private final ApplicationEvaluationService service;private final CurrentActor actor;
    public ApplicationEvaluationController(ApplicationEvaluationService service,CurrentActor actor){this.service=service;this.actor=actor;}
    public record Decision(@NotNull ApplicationStatus decision,@Size(max=2000) String reason,@NotNull @PositiveOrZero Long expectedVersion) {}
    @GetMapping
    public PageResult<ApplicationEvaluationService.Candidate> list(@PathVariable("id") UUID campaignId,@RequestParam(required=false) ApplicationStatus status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(actor.accountId(),new CampaignId(campaignId),status,new PageRequest(page,size));}
    @GetMapping("/{applicationId}")
    public ApplicationEvaluationService.Candidate get(@PathVariable("id") UUID campaignId,@PathVariable("applicationId") UUID id){return service.get(actor.accountId(),new CampaignId(campaignId),new ApplicationId(id));}
    @PostMapping("/{applicationId}/decision")
    public ApplicationEvaluationService.Candidate decide(@PathVariable("id") UUID campaignId,@PathVariable("applicationId") UUID id,@Valid @RequestBody Decision body){
        return service.decide(actor.accountId(),new CampaignId(campaignId),new ApplicationId(id),body.decision(),body.reason(),body.expectedVersion());
    }
}
