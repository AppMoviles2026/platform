package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.queries.GetPublishedCampaignsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
import com.collabtech.platform.shared.application.pagination.PageResult;

public final class GetPublishedCampaignsQueryHandler implements QueryHandler<GetPublishedCampaignsQuery, PageResult<CampaignViews.Summary>> {
    private final CampaignCatalog catalog;
    public GetPublishedCampaignsQueryHandler(CampaignCatalog catalog) { this.catalog = catalog; }
    public PageResult<CampaignViews.Summary> handle(GetPublishedCampaignsQuery query) {
        return GetBrandCampaignsQueryHandler.map(catalog.published(query.page()), catalog);
    }
}
