package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.queries.SearchCampaignsQuery;
import com.collabtech.platform.campaign.application.ports.CampaignReadRepository;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
import com.collabtech.platform.shared.application.pagination.PageResult;
public final class SearchCampaignsQueryHandler implements QueryHandler<SearchCampaignsQuery,PageResult<CampaignViews.Summary>> {
    private final CampaignReadRepository reads;
    public SearchCampaignsQueryHandler(CampaignReadRepository reads) { this.reads = reads; }
    public PageResult<CampaignViews.Summary> handle(SearchCampaignsQuery query) { return reads.search(query); }
}
