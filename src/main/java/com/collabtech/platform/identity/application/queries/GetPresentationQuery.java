package com.collabtech.platform.identity.application.queries;

import com.collabtech.platform.identity.application.projections.PresentationAudience;
import com.collabtech.platform.identity.application.projections.PublicPresentationView;
import com.collabtech.platform.shared.application.cqrs.Query;
import java.util.Objects;

public record GetPresentationQuery(PresentationAudience audience) implements Query<PublicPresentationView> {
    public GetPresentationQuery {
        Objects.requireNonNull(audience, "audience");
    }
}
