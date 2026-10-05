package com.collabtech.platform.identity.interfaces.rest.resources;

import java.util.List;

public record PresentationResource(String audience, String language, String title,
                                   String valueProposition, List<BenefitResource> benefits) {
    public PresentationResource {
        benefits = List.copyOf(benefits);
    }
}
