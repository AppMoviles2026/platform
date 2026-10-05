package com.collabtech.platform.campaign.infrastructure.acl;

import com.collabtech.platform.campaign.application.ports.CampaignActorGateway;
import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** Cross-context access uses only Identity's published facade; never its repositories/tables. */
@Component
@Profile("!skeleton")
public class IdentityCampaignActorAdapter implements CampaignActorGateway {
    private final IdentityProfileFacade identity;
    public IdentityCampaignActorAdapter(IdentityProfileFacade identity) { this.identity = identity; }
    public Brand getActiveBrand(UUID accountId) {
        var profile = active(accountId);
        if (!profile.type().equals("BRAND")) throw new CampaignFailure(CampaignFailure.Code.BRAND_REQUIRED);
        return new Brand(new BrandId(profile.profileId()), profile.name(), profile.location());
    }
    public void requireActiveCreator(UUID accountId) {
        if (!active(accountId).type().equals("CREATOR")) throw new CampaignFailure(CampaignFailure.Code.CREATOR_REQUIRED);
    }
    private IdentityProfileFacade.Profile active(UUID id) {
        var profile = identity.byAccount(id).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.ACCOUNT_NOT_ACTIVE));
        if (!profile.active()) throw new CampaignFailure(CampaignFailure.Code.ACCOUNT_NOT_ACTIVE);
        return profile;
    }
}
