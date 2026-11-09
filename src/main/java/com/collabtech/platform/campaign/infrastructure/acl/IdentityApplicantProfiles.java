package com.collabtech.platform.campaign.infrastructure.acl;
import com.collabtech.platform.campaign.application.ports.ApplicantProfiles;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
@Component @Profile("!skeleton")
public class IdentityApplicantProfiles implements ApplicantProfiles {
    private final IdentityProfileFacade identity;
    public IdentityApplicantProfiles(IdentityProfileFacade identity){this.identity=identity;}
    public Optional<ApplicantProfiles.Profile> find(UUID id){return identity.creatorById(id).map(p->new ApplicantProfiles.Profile(p.profileId(),p.displayName(),p.biography(),p.niche(),p.audienceDescription(),p.location(),p.active(),p.socialAccounts().stream().map(s->new Social(s.socialId(),s.platform(),s.username())).toList()));}
}
