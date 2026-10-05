package com.collabtech.platform.campaign.application.handlers;

import com.collabtech.platform.campaign.application.queries.GetBrandCampaignsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
import com.collabtech.platform.shared.application.pagination.PageResult;

/** Owner authorization happens before constructing this query. */
public final class GetBrandCampaignsQueryHandler implements QueryHandler<GetBrandCampaignsQuery, PageResult<CampaignViews.Summary>> {
    private final CampaignCatalog catalog;
    public GetBrandCampaignsQueryHandler(CampaignCatalog catalog) { this.catalog = catalog; }
    public PageResult<CampaignViews.Summary> handle(GetBrandCampaignsQuery query) {
        var result = catalog.byBrand(query.brandId(), query.page());
        return map(result, catalog);
    }
    static PageResult<CampaignViews.Summary> map(PageResult<Campaign> result, CampaignCatalog catalog) {
        return new PageResult<>(result.items().stream().map(campaign -> new CampaignViews.Summary(campaign.id().value(), campaign.brandId().value(),
                catalog.brandName(campaign.id()), campaign.title(), campaign.category(), campaign.location(), campaign.compensationTerms(),
                campaign.applicationDeadline(), campaign.status().name())).toList(), result.total(), result.page(), result.size());
    }
}
