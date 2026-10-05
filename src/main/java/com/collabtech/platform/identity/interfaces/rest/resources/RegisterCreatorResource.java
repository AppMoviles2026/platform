package com.collabtech.platform.identity.interfaces.rest.resources;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCreatorResource(
        @NotBlank @Size(max = 150) String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password) {
    public RegisterCreatorResource {
        displayName = displayName == null ? null : displayName.strip();
        email = email == null ? null : email.strip();
    }
    @Override public String toString() { return "RegisterCreatorResource[sensitive fields redacted]"; }
}
