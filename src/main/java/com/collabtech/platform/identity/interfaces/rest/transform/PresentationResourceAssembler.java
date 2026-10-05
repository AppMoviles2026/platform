package com.collabtech.platform.identity.interfaces.rest.transform;

import com.collabtech.platform.identity.application.projections.PublicPresentationView;
import com.collabtech.platform.identity.interfaces.rest.resources.BenefitResource;
import com.collabtech.platform.identity.interfaces.rest.resources.PresentationResource;

public final class PresentationResourceAssembler {
    private PresentationResourceAssembler() {}

    public static PresentationResource toResource(PublicPresentationView presentation) {
        var benefits = presentation.benefits().stream()
                .map(benefit -> new BenefitResource(benefit.code(), benefit.title(), benefit.description()))
                .toList();
        return new PresentationResource(presentation.audience().name(), presentation.language(),
                presentation.title(), presentation.valueProposition(), benefits);
    }
}
