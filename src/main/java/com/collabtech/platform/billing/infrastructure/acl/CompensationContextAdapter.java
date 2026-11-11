package com.collabtech.platform.billing.infrastructure.acl;
import com.collabtech.platform.billing.application.ports.CompensationPorts.*;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationExecutionFacade;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure;
import com.collabtech.platform.billing.domain.exceptions.BillingFailure.Code;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component @Profile("!skeleton")
public class CompensationContextAdapter implements Context {
    private final IdentityProfileFacade identity;private final CollaborationExecutionFacade collaborations;
    public CompensationContextAdapter(IdentityProfileFacade identity,CollaborationExecutionFacade collaborations){this.identity=identity;this.collaborations=collaborations;}
    public Actor actor(UUID account){var p=identity.byAccount(account).filter(IdentityProfileFacade.Profile::active).orElseThrow(()->new BillingFailure(Code.ACCOUNT_NOT_ACTIVE));return new Actor(account,p.profileId(),p.type());}
    public Party party(UUID profile,String role){var p=identity.accountByProfile(profile,role).orElseThrow(()->new BillingFailure(Code.NOT_FOUND));return new Party(p.accountId(),p.profileId(),p.role(),p.active());}
    public Optional<Execution> execution(UUID id,boolean lock){return (lock?collaborations.findForUpdate(id):collaborations.find(id)).map(e->new Execution(
        e.collaborationId(),e.agreementId(),e.brandProfileId(),e.creatorProfileId(),e.status(),e.fulfilled(),e.compensationEligible(),e.openIncidentCount(),e.version(),
        e.compensation().type(),e.compensation().amount(),e.compensation().currency(),e.compensation().description()));}
}
