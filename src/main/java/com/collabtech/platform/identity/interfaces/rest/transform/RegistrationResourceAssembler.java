package com.collabtech.platform.identity.interfaces.rest.transform;

import com.collabtech.platform.identity.application.commands.RegisterBrandCommand;
import com.collabtech.platform.identity.application.commands.RegisterCreatorCommand;
import com.collabtech.platform.identity.application.projections.IdentityViews;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.interfaces.rest.resources.AccountResource;
import com.collabtech.platform.identity.interfaces.rest.resources.RegisterBrandResource;
import com.collabtech.platform.identity.interfaces.rest.resources.RegisterCreatorResource;

public final class RegistrationResourceAssembler {
    private RegistrationResourceAssembler() {}
    public static RegisterBrandCommand toCommand(RegisterBrandResource resource) {
        return new RegisterBrandCommand(resource.businessName(), new EmailAddress(resource.email()), resource.password());
    }
    public static RegisterCreatorCommand toCommand(RegisterCreatorResource resource) {
        return new RegisterCreatorCommand(resource.displayName(), new EmailAddress(resource.email()), resource.password());
    }
    public static AccountResource toResource(IdentityViews.AccountView view) {
        return new AccountResource(view.accountId(), view.profileId(), view.name(), view.accountType(), view.status());
    }
}
