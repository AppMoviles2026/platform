package com.collabtech.platform.identity.interfaces.rest;

import com.collabtech.platform.identity.application.handlers.RegisterBrandCommandHandler;
import com.collabtech.platform.identity.application.handlers.RegisterCreatorCommandHandler;
import com.collabtech.platform.identity.interfaces.rest.resources.AccountResource;
import com.collabtech.platform.identity.interfaces.rest.resources.RegisterBrandResource;
import com.collabtech.platform.identity.interfaces.rest.resources.RegisterCreatorResource;
import com.collabtech.platform.identity.interfaces.rest.transform.RegistrationResourceAssembler;
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

    public AuthController(RegisterBrandCommandHandler registerBrand, RegisterCreatorCommandHandler registerCreator) {
        this.registerBrand = registerBrand;
        this.registerCreator = registerCreator;
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
}
