package com.collabtech.platform.identity.application.projections;

import java.util.List;
import java.util.Objects;

/** Published read-only content; no user identity or mutable business lifecycle. */
public record PublicPresentationView(PresentationAudience audience, String language, String title,
                                     String valueProposition, List<PresentationBenefitView> benefits) {
    public PublicPresentationView {
        Objects.requireNonNull(audience, "audience");
        if (language == null || language.isBlank() || title == null || title.isBlank()
                || valueProposition == null || valueProposition.isBlank()) {
            throw new IllegalArgumentException("Presentation copy is required");
        }
        benefits = List.copyOf(benefits);
        if (benefits.isEmpty()
                || benefits.stream().map(PresentationBenefitView::code).distinct().count() != benefits.size()) {
            throw new IllegalArgumentException("Non-empty benefits with distinct codes are required");
        }
    }
}
