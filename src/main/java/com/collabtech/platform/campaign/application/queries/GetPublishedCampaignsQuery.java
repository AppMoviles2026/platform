package com.collabtech.platform.campaign.application.queries;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.shared.application.cqrs.Query;
import com.collabtech.platform.shared.application.pagination.*;

/** Basic visibility required by US15, without US17 text/category search. */
public record GetPublishedCampaignsQuery(PageRequest page) implements Query<PageResult<CampaignViews.Summary>> {}
