package com.collabtech.platform.identity.interfaces.rest;

import com.collabtech.platform.identity.application.services.IdentityApplicationService;
import com.collabtech.platform.identity.application.commands.StartSocialAuthorizationCommand;
import com.collabtech.platform.identity.application.commands.CompleteSocialAuthorizationCommand;
import com.collabtech.platform.identity.application.queries.GetLinkedSocialMediaQuery;
import com.collabtech.platform.identity.domain.model.valueobjects.AccountId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialPlatform;
import com.collabtech.platform.shared.application.security.CurrentActor;
import com.collabtech.platform.identity.interfaces.rest.resources.IdentityResources;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!skeleton")
@RequestMapping("/api/v1/social-accounts")
public class SocialMediaController {
    private final IdentityApplicationService identity; private final CurrentActor actor;
    public SocialMediaController(IdentityApplicationService identity, CurrentActor actor) { this.identity = identity; this.actor = actor; }
    @PostMapping("/{platform}/authorizations")
    public IdentityResources.Authorization authorize(@PathVariable String platform) {
        return new IdentityResources.Authorization(identity.handle(new StartSocialAuthorizationCommand(
                new AccountId(actor.accountId()), new SocialPlatform(platform))).authorizationUrl());
    }
    @GetMapping("/{platform}/callback")
    public IdentityResources.SocialAccount callback(@PathVariable String platform, @RequestParam String state,
            @RequestParam(required=false) String code, @RequestParam(required=false) String error) {
        return IdentityResources.social(identity.handle(new CompleteSocialAuthorizationCommand(new SocialPlatform(platform), state, code, error)));
    }
    @GetMapping("/me")
    public List<IdentityResources.SocialAccount> linked() {
        return identity.handle(new GetLinkedSocialMediaQuery(new AccountId(actor.accountId()))).stream().map(IdentityResources::social).toList();
    }
}
