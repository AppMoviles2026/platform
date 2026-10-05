package com.collabtech.platform.identity.interfaces.rest.resources;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterBrandResource(
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password) {
    public RegisterBrandResource {
        businessName = businessName == null ? null : businessName.strip();
        email = email == null ? null : email.strip();
    }
    @Override public String toString() { return "RegisterBrandResource[sensitive fields redacted]"; }
}
