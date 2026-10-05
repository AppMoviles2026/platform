package com.collabtech.platform.identity.interfaces.rest;

import com.collabtech.platform.identity.application.handlers.RegisterBrandCommandHandler;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.interfaces.rest.resources.AccountResource;
import com.collabtech.platform.identity.interfaces.rest.resources.RegisterBrandResource;
import com.collabtech.platform.identity.interfaces.rest.resources.RegisterCreatorResource;
import com.collabtech.platform.identity.interfaces.rest.transform.RegistrationResourceAssembler;
import com.collabtech.platform.identity.application.services.IdentityApplicationService;
import com.collabtech.platform.identity.application.commands.AuthenticateAccountCommand;
import com.collabtech.platform.identity.application.commands.RecoverAccountCommand;
import com.collabtech.platform.identity.application.commands.ResetPasswordCommand;
import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;
import com.collabtech.platform.identity.interfaces.rest.resources.IdentityRequests;
import com.collabtech.platform.identity.interfaces.rest.resources.IdentityResources;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!skeleton")
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {
    private final RegisterBrandCommandHandler registerBrand;
    private final RegisterCreatorCommandHandler registerCreator;
    private final IdentityApplicationService identity;

    public AuthController(RegisterBrandCommandHandler registerBrand, RegisterCreatorCommandHandler registerCreator, IdentityApplicationService identity) {
        this.registerBrand = registerBrand;
        this.registerCreator = registerCreator;
        this.identity = identity;
    }

    @PostMapping(value = "/brands", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResource registerBrand(@Valid @RequestBody RegisterBrandResource resource) {
        return RegistrationResourceAssembler.toResource(registerBrand.handle(RegistrationResourceAssembler.toCommand(resource)));
    }

    @PostMapping(value = "/creators", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResource registerCreator(@Valid @RequestBody RegisterCreatorResource resource) {
        return RegistrationResourceAssembler.toResource(registerCreator.handle(RegistrationResourceAssembler.toCommand(resource)));
    }

    @PostMapping(value = "/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IdentityResources.Session login(@Valid @RequestBody IdentityRequests.Login request) {
        return IdentityResources.session(identity.handle(new AuthenticateAccountCommand(new EmailAddress(request.email()), request.password())));
    }

    @PostMapping(value = "/recovery-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IdentityResources.RecoveryAccepted recover(@Valid @RequestBody IdentityRequests.Recovery request) {
        identity.handle(new RecoverAccountCommand(new EmailAddress(request.email())));
        return new IdentityResources.RecoveryAccepted("Si existe una cuenta habilitada, recibirás instrucciones para recuperar el acceso.");
    }

    @PostMapping(value = "/password-resets", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody IdentityRequests.PasswordReset request) {
        identity.handle(new ResetPasswordCommand(request.token(), request.newPassword()));
    }
}
