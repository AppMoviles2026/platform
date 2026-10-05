package com.collabtech.platform.identity.interfaces.rest;

import com.collabtech.platform.identity.application.services.IdentityApplicationService;
import com.collabtech.platform.identity.application.commands.UpdateCreatorProfileCommand;
import com.collabtech.platform.identity.application.queries.GetUserProfileQuery;
import com.collabtech.platform.identity.application.queries.GetCurrentAccountQuery;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.identity.interfaces.rest.resources.AccountResource;
import com.collabtech.platform.identity.interfaces.rest.resources.IdentityRequests;
import com.collabtech.platform.identity.interfaces.rest.resources.IdentityResources;
import com.collabtech.platform.identity.interfaces.rest.transform.RegistrationResourceAssembler;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!skeleton")
@RequestMapping("/api/v1")
public class UserProfileController {
    private final IdentityApplicationService identity; private final CurrentActor actor;
    public UserProfileController(IdentityApplicationService identity, CurrentActor actor) { this.identity = identity; this.actor = actor; }
    @GetMapping("/accounts/me")
    public AccountResource currentAccount() {
        return RegistrationResourceAssembler.toResource(identity.handle(new GetCurrentAccountQuery(new AccountId(actor.accountId()))));
    }
    @GetMapping("/profiles/me/creator")
    public IdentityResources.CreatorProfile creatorProfile() {
        return IdentityResources.profile(identity.handle(new GetUserProfileQuery(new AccountId(actor.accountId()))));
    }
    @PutMapping("/profiles/me/creator")
    public IdentityResources.CreatorProfile updateCreator(@Valid @RequestBody IdentityRequests.CreatorProfile request) {
        return IdentityResources.profile(identity.handle(new UpdateCreatorProfileCommand(new AccountId(actor.accountId()),
                request.displayName(), request.biography(), request.niche(), request.audienceDescription(), request.location())));
    }
}
