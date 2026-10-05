package com.collabtech.platform.campaign.application.handlers;
import com.collabtech.platform.campaign.application.queries.GetCampaignDetailsQuery;
import com.collabtech.platform.campaign.application.ports.CampaignReadRepository;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.cqrs.QueryHandler;
public final class GetCampaignDetailsQueryHandler implements QueryHandler<GetCampaignDetailsQuery,CampaignViews.Details> {
    private final CampaignReadRepository reads;
    public GetCampaignDetailsQueryHandler(CampaignReadRepository reads) { this.reads = reads; }
    public CampaignViews.Details handle(GetCampaignDetailsQuery query) {
        var result = reads.findDetails(query).orElseThrow(() -> new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_FOUND));
        if (result.publicationDate() == null || result.summary().status().equals("DRAFT")) throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
        return result;
    }
}
