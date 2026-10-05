package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.ports.ApplicationReadRepository;
import com.collabtech.platform.campaign.application.queries.GetApplicationDetailsQuery;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
public final class GetApplicationDetailsQueryHandler implements QueryHandler<GetApplicationDetailsQuery,CampaignViews.ApplicationView> {
    private final ApplicationReadRepository reads;
    public GetApplicationDetailsQueryHandler(ApplicationReadRepository reads) { this.reads=reads; }
    public CampaignViews.ApplicationView handle(GetApplicationDetailsQuery query) { return reads.findDetails(query).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.APPLICATION_NOT_FOUND)); }
}
