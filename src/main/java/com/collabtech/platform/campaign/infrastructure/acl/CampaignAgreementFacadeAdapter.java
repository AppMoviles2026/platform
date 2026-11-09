package com.collabtech.platform.campaign.infrastructure.acl;

import com.collabtech.platform.campaign.interfaces.acl.CampaignAgreementFacade;
import com.collabtech.platform.campaign.domain.repositories.ApplicationRepository;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationId;
import com.collabtech.platform.campaign.domain.model.valueobjects.ApplicationStatus;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("!skeleton")
public class CampaignAgreementFacadeAdapter implements CampaignAgreementFacade {
    private final ApplicationRepository applications; private final CampaignRepository campaigns; private final IdentityProfileFacade identity;
    public CampaignAgreementFacadeAdapter(ApplicationRepository applications,CampaignRepository campaigns,IdentityProfileFacade identity){this.applications=applications;this.campaigns=campaigns;this.identity=identity;}
    @Transactional
    public Selection selectedFor(UUID account,UUID applicationId){
        var actor=identity.byAccount(account).filter(IdentityProfileFacade.Profile::active).orElseThrow(()->new Unavailable(Unavailable.Reason.INACTIVE));
        var a=applications.findById(new ApplicationId(applicationId)).orElseThrow(()->new Unavailable(Unavailable.Reason.NOT_FOUND));
        // Lock the stable campaign before the caller creates an agreement; concurrent creations share this lock.
        var c=campaigns.findByIdForUpdate(a.campaignId()).orElseThrow(()->new Unavailable(Unavailable.Reason.NOT_FOUND));
        boolean participant=actor.type().equals("BRAND") ? actor.profileId().equals(c.brandId().value()) : actor.profileId().equals(a.creatorId().value());
        if(!participant)throw new Unavailable(Unavailable.Reason.FORBIDDEN);
        if(a.status()!=ApplicationStatus.SELECTED)throw new Unavailable(Unavailable.Reason.NOT_SELECTED);
        var money=c.compensationTerms();
        if(money==null||c.deliverables().isEmpty())throw new Unavailable(Unavailable.Reason.NOT_SELECTED);
        return new Selection(a.id().value(),a.version(),a.decidedAt(),c.id().value(),c.version(),c.brandId().value(),a.creatorId().value(),
                c.title(),c.objective(),c.description(),c.category(),c.targetAudience(),c.location(),a.message(),
                a.confirmations().stream().map(r->r.value()).collect(Collectors.toSet()),
                c.requirements().stream().map(r->new Requirement(r.id().value(),r.description(),r.mandatory(),r.rule().type().name(),r.rule().expectedValue())).toList(),
                c.deliverables().stream().map(d->new Deliverable(d.id().value(),d.contentType(),d.description(),d.quantity(),d.deadline())).toList(),
                money.type().name(),money.amount(),money.currency(),money.description(),c.applicationDeadline());
    }
}
