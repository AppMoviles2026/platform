package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.ports.ApplicationReadRepository;
import com.collabtech.platform.campaign.application.queries.GetCreatorApplicationsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
import com.collabtech.platform.shared.application.pagination.PageResult;
public final class GetCreatorApplicationsQueryHandler implements QueryHandler<GetCreatorApplicationsQuery,PageResult<CampaignViews.ApplicationView>> {
    private final ApplicationReadRepository reads;
    public GetCreatorApplicationsQueryHandler(ApplicationReadRepository reads) { this.reads=reads; }
    public PageResult<CampaignViews.ApplicationView> handle(GetCreatorApplicationsQuery query) { return reads.findByCreator(query); }
}
