package com.collabtech.platform.campaign.application.handlers;

import com.collabtech.platform.campaign.application.queries.GetBrandCampaignsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
import com.collabtech.platform.shared.application.pagination.PageResult;
import java.time.Clock;
import java.time.Instant;
import com.collabtech.platform.campaign.application.projections.CampaignViewMapper;

/** Owner authorization happens before constructing this query. */
public final class GetBrandCampaignsQueryHandler implements QueryHandler<GetBrandCampaignsQuery, PageResult<CampaignViews.Summary>> {
    private final CampaignCatalog catalog;
    private final Clock clock;
    public GetBrandCampaignsQueryHandler(CampaignCatalog catalog, Clock clock) { this.catalog = catalog; this.clock = clock; }
    public PageResult<CampaignViews.Summary> handle(GetBrandCampaignsQuery query) {
        var result = catalog.byBrand(query.brandId(), query.page());
        return map(result, catalog, clock.instant());
    }
    static PageResult<CampaignViews.Summary> map(PageResult<Campaign> result, CampaignCatalog catalog, Instant now) {
        return new PageResult<>(result.items().stream().map(campaign -> CampaignViewMapper.summary(campaign,
                catalog.brandName(campaign.id()), now)).toList(), result.total(), result.page(), result.size());
    }
}
