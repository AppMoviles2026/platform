package com.collabtech.platform.collaboration.infrastructure.acl;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.SelectedTerms;
import com.collabtech.platform.collaboration.domain.model.AgreementSnapshot;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.campaign.interfaces.acl.CampaignAgreementFacade;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
@Component @Profile("!skeleton")
public class SelectedCampaignTermsAdapter implements SelectedTerms {
    private final CampaignAgreementFacade campaigns;private final IdentityProfileFacade identity;
    public SelectedCampaignTermsAdapter(CampaignAgreementFacade campaigns,IdentityProfileFacade identity){this.campaigns=campaigns;this.identity=identity;}
    public AgreementSnapshot capture(UUID actor,UUID application,Instant now){
        CampaignAgreementFacade.Selection s;
        try{s=campaigns.selectedFor(actor,application);}catch(CampaignAgreementFacade.Unavailable ex){throw new CollaborationFailure(switch(ex.reason()){
            case NOT_FOUND->Code.NOT_FOUND;case FORBIDDEN->Code.FORBIDDEN;case NOT_SELECTED->Code.SOURCE_NOT_SELECTED;case INACTIVE->Code.ACCOUNT_NOT_ACTIVE;});}
        var brand=identity.brandById(s.brandId()).filter(IdentityProfileFacade.BrandDetails::active).orElseThrow(()->new CollaborationFailure(Code.ACCOUNT_NOT_ACTIVE));
        var creator=identity.creatorById(s.creatorId()).filter(IdentityProfileFacade.CreatorDetails::active).orElseThrow(()->new CollaborationFailure(Code.ACCOUNT_NOT_ACTIVE));
        var deadline=s.deliverables().stream().map(CampaignAgreementFacade.Deliverable::deadline).min(Comparator.naturalOrder()).orElseThrow();
        return new AgreementSnapshot(UUID.randomUUID(),s.applicationId(),s.applicationVersion(),s.selectedAt(),s.campaignId(),s.campaignVersion(),s.brandId(),s.creatorId(),
                brand.businessName(),creator.displayName(),s.title(),s.objective(),s.description(),s.category(),s.targetAudience(),s.location(),s.message(),s.confirmedRequirements(),
                s.requirements().stream().map(r->new AgreementSnapshot.Requirement(r.id(),r.description(),r.mandatory(),r.ruleType(),r.expectedValue())).toList(),
                s.deliverables().stream().map(d->new AgreementSnapshot.DeliverableTerm(d.id(),d.contentType(),d.description(),d.quantity(),d.deadline())).toList(),
                new AgreementSnapshot.Compensation(s.compensationType(),s.amount(),s.currency(),s.compensationDescription()),s.applicationDeadline(),now,deadline);
    }
}
