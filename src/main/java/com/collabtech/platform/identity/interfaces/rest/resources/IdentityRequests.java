package com.collabtech.platform.identity.interfaces.rest.resources;

import jakarta.validation.constraints.*;

public final class IdentityRequests {
    private IdentityRequests() {}
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=128) String password) {
        public Login { email = email == null ? null : email.strip(); }
        @Override public String toString() { return "Login[redacted]"; }
    }
    public record Recovery(@NotBlank @Email @Size(max=254) String email) {
        public Recovery { email = email == null ? null : email.strip(); }
    }
    public record PasswordReset(@NotBlank @Size(max=128) String token, @NotBlank @Size(min=8,max=128) String newPassword) {
        @Override public String toString() { return "PasswordReset[redacted]"; }
    }
    public record CreatorProfile(@NotBlank @Size(max=150) String displayName,
            @Size(max=2000) String biography, @Size(max=150) String niche,
            @Size(max=2000) String audienceDescription, @Size(max=150) String location) {
        public CreatorProfile { displayName = displayName == null ? null : displayName.strip(); }
    }
}
