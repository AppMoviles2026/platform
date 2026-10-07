package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.queries.GetPublishedCampaignsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
import com.collabtech.platform.shared.application.pagination.PageResult;
import java.time.Clock;

public final class GetPublishedCampaignsQueryHandler implements QueryHandler<GetPublishedCampaignsQuery, PageResult<CampaignViews.Summary>> {
    private final CampaignCatalog catalog;
    private final Clock clock;
    public GetPublishedCampaignsQueryHandler(CampaignCatalog catalog, Clock clock) { this.catalog = catalog; this.clock = clock; }
    public PageResult<CampaignViews.Summary> handle(GetPublishedCampaignsQuery query) {
        return GetBrandCampaignsQueryHandler.map(catalog.published(query.page()), catalog, clock.instant());
    }
}
