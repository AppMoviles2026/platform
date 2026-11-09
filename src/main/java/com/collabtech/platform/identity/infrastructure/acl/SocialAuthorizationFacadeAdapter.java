package com.collabtech.platform.identity.infrastructure.acl;

import com.collabtech.platform.identity.domain.model.valueobjects.CreatorProfileId;
import com.collabtech.platform.identity.domain.model.valueobjects.SocialMediaAccountId;
import com.collabtech.platform.identity.infrastructure.oauth.DatabaseSocialAuthorizationAccess;
import com.collabtech.platform.identity.interfaces.acl.SocialAuthorizationFacade;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!skeleton")
public class SocialAuthorizationFacadeAdapter implements SocialAuthorizationFacade {
    private final DatabaseSocialAuthorizationAccess credentials;
    public SocialAuthorizationFacadeAdapter(DatabaseSocialAuthorizationAccess credentials) { this.credentials = credentials; }
    public Status status(UUID owner, UUID id, Set<String> scopes) {
        var view = credentials.inspect(new CreatorProfileId(owner), new SocialMediaAccountId(id), scopes);
        return new Status(view.socialId(), view.platform(), view.status(), view.grantedScopes(), view.missingScopes(),
                view.expiresAt(), view.renewable(), view.reauthorizationRequired());
    }
    public <T> T withAuthorizedAccess(UUID owner, UUID id, Set<String> scopes, AuthorizedOperation<T> operation) {
        java.util.Objects.requireNonNull(operation);
        var profile = new CreatorProfileId(owner); var social = new SocialMediaAccountId(id);
        var state = credentials.ensureUsable(profile, social, scopes);
        return operation.execute(new TokenAccess(credentials.accessToken(profile, social, scopes), state.platform()));
    }
}
