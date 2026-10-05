package com.collabtech.platform.identity.application.projections;

/** Stable benefit code plus human-readable public copy. */
public record PresentationBenefitView(String code, String title, String description) {
    public PresentationBenefitView {
        if (code == null || code.isBlank() || title == null || title.isBlank()
                || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Benefit code, title and description are required");
        }
    }
}
