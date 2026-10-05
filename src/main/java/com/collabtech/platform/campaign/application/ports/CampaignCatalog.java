package com.collabtech.platform.campaign.application.ports;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;
import com.collabtech.platform.shared.application.pagination.PageRequest;
import com.collabtech.platform.shared.application.pagination.PageResult;

/** Minimal US15 visibility and owner retrieval; not US17 search or US19 applications. */
public interface CampaignCatalog {
    PageResult<Campaign> byBrand(BrandId brand, PageRequest page);
    PageResult<Campaign> published(PageRequest page);
    String brandName(com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId id);
    void saveNew(Campaign campaign, String brandName);
}
