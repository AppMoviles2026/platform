package com.collabtech.platform.identity.application.handlers;

import com.collabtech.platform.identity.application.ports.PresentationContentProvider;
import com.collabtech.platform.identity.application.queries.GetPresentationQuery;
import com.collabtech.platform.identity.application.projections.PublicPresentationView;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
import java.util.Objects;

public final class GetPresentationQueryHandler implements QueryHandler<GetPresentationQuery, PublicPresentationView> {
    private final PresentationContentProvider contentProvider;

    public GetPresentationQueryHandler(PresentationContentProvider contentProvider) {
        this.contentProvider = Objects.requireNonNull(contentProvider, "contentProvider");
    }

    @Override
    public PublicPresentationView handle(GetPresentationQuery query) {
        Objects.requireNonNull(query, "query");
        return contentProvider.findByAudience(query.audience());
    }
}
