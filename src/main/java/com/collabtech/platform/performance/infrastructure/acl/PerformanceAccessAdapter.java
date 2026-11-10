package com.collabtech.platform.performance.infrastructure.acl;
import com.collabtech.platform.performance.application.ports.PerformancePorts.*;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.Code;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationExecutionFacade;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationContentFacade;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component @Profile("!skeleton")
public class PerformanceAccessAdapter implements Access {
    private final IdentityProfileFacade identity;private final CollaborationExecutionFacade collaborations;private final CollaborationContentFacade contents;
    public PerformanceAccessAdapter(IdentityProfileFacade identity,CollaborationExecutionFacade collaborations,CollaborationContentFacade contents) {
        this.identity=identity;this.collaborations=collaborations;this.contents=contents;
    }
    public Actor actor(UUID account) {
        var profile=identity.byAccount(account).filter(IdentityProfileFacade.Profile::active).orElseThrow(()->new PerformanceFailure(Code.ACCOUNT_NOT_ACTIVE));
        return new Actor(account,profile.profileId(),profile.type());
    }
    public Scope scope(UUID id,boolean lock) {
        var c=(lock?collaborations.findForUpdate(id):collaborations.find(id)).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
        return new Scope(c.collaborationId(),c.brandProfileId(),c.creatorProfileId(),c.cancelledAt()!=null||c.status().equals("CANCELLED"));
    }
    public Content content(UUID id,UUID submission) {
        var c=contents.find(id,submission).orElseThrow(()->new PerformanceFailure(Code.NOT_FOUND));
        return new Content(c.submissionId(),c.deliverableId(),c.url(),c.current(),c.reviewStatus());
    }
    public Set<UUID> currentSubmissions(UUID id){return contents.currentSubmissions(id);}
    public String socialPlatform(UUID creator,UUID social) {
        var profile=identity.creatorById(creator).filter(IdentityProfileFacade.CreatorDetails::active)
                .orElseThrow(()->new PerformanceFailure(Code.ACCOUNT_NOT_ACTIVE));
        return profile.socialAccounts().stream().filter(s->s.socialId().equals(social)).map(IdentityProfileFacade.SocialIdentity::platform)
                .findFirst().orElseThrow(()->new PerformanceFailure(Code.FORBIDDEN));
    }
}
