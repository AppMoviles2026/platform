package com.collabtech.platform.identity.interfaces.rest;

import com.collabtech.platform.identity.application.handlers.GetPresentationQueryHandler;
import com.collabtech.platform.identity.application.queries.GetPresentationQuery;
import com.collabtech.platform.identity.application.projections.PresentationAudience;
import com.collabtech.platform.identity.interfaces.rest.resources.PresentationResource;
import com.collabtech.platform.identity.interfaces.rest.transform.PresentationResourceAssembler;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/public/presentations", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicPresentationController {
    private final GetPresentationQueryHandler queryHandler;

    public PublicPresentationController(GetPresentationQueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    @GetMapping("/brands")
    public PresentationResource getBrandPresentation() {
        return PresentationResourceAssembler.toResource(queryHandler.handle(new GetPresentationQuery(PresentationAudience.BRAND)));
    }

    @GetMapping("/creators")
    public PresentationResource getCreatorPresentation() {
        return PresentationResourceAssembler.toResource(queryHandler.handle(new GetPresentationQuery(PresentationAudience.CREATOR)));
    }
}
