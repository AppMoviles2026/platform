package com.collabtech.platform.collaboration.infrastructure.acl;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.domain.model.Agreement;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure.Code;
import com.collabtech.platform.identity.interfaces.acl.IdentityProfileFacade;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
@Component @Profile("!skeleton")
public class CollaborationIdentityAdapter implements Participants {
    private final IdentityProfileFacade identity;
    public CollaborationIdentityAdapter(IdentityProfileFacade identity){this.identity=identity;}
    public Actor actor(UUID id){var p=identity.byAccount(id).filter(IdentityProfileFacade.Profile::active).orElseThrow(()->new CollaborationFailure(Code.ACCOUNT_NOT_ACTIVE));
        return new Actor(id,p.profileId(),Agreement.Party.valueOf(p.type()));}
    public boolean active(UUID id,Agreement.Party party){return party==Agreement.Party.BRAND?identity.brandById(id).map(IdentityProfileFacade.BrandDetails::active).orElse(false)
            :identity.creatorById(id).map(IdentityProfileFacade.CreatorDetails::active).orElse(false);}
}
