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
import java.util.UUID;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import com.collabtech.platform.identity.application.ports.AuthorizationClient;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!skeleton")
@RequestMapping("/api/v1/social-accounts")
public class SocialMediaController {
    private final IdentityApplicationService identity; private final CurrentActor actor;
    public SocialMediaController(IdentityApplicationService identity, CurrentActor actor) { this.identity = identity; this.actor = actor; }
    @PostMapping("/{platform}/authorizations")
    public IdentityResources.Authorization authorize(@PathVariable String platform, @RequestParam(defaultValue="API") AuthorizationClient client) {
        var view = identity.handle(new StartSocialAuthorizationCommand(new AccountId(actor.accountId()), new SocialPlatform(platform), client));
        return new IdentityResources.Authorization(view.authorizationUrl(), view.authorizationId());
    }
    @GetMapping("/{platform}/callback")
    public ResponseEntity<?> callback(@PathVariable String platform, @RequestParam String state,
            @RequestParam(required=false) String code, @RequestParam(required=false) String error) {
        var result = identity.completeSocialAuthorization(new CompleteSocialAuthorizationCommand(new SocialPlatform(platform), state, code, error));
        if (result.authorization().client() == AuthorizationClient.ANDROID) {
            // Only an opaque attempt ID crosses the application link. No state, code, tokens or external redirect input.
            return ResponseEntity.status(303).location(URI.create("collabpro://social-authorization-completed?authorizationId="
                    + result.authorization().authorizationId())).header("Cache-Control","no-store").header("Referrer-Policy","no-referrer").build();
        }
        if (result.failure() != null) throw result.failure();
        return ResponseEntity.ok().header("Cache-Control","no-store").body(IdentityResources.social(result.socialAccount()));
    }
    @GetMapping("/authorizations/{authorizationId}")
    public ResponseEntity<IdentityViews.AuthorizationStatusView> authorizationStatus(@PathVariable UUID authorizationId) {
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(identity.authorizationStatus(new AccountId(actor.accountId()), authorizationId));
    }
    @GetMapping("/me")
    public List<IdentityResources.SocialAccount> linked() {
        return identity.handle(new GetLinkedSocialMediaQuery(new AccountId(actor.accountId()))).stream().map(IdentityResources::social).toList();
    }
}
