package com.collabtech.platform.campaign.application.services;
import com.collabtech.platform.campaign.application.ports.*;
import com.collabtech.platform.campaign.application.queries.*;
import com.collabtech.platform.campaign.application.handlers.*;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.util.UUID;
public final class CampaignDiscoveryService {
    private final CampaignActorGateway actors; private final CampaignUnitOfWork transactions; private final CampaignReadRepository reads;
    private final CampaignPreparationService preparation;
    public CampaignDiscoveryService(CampaignActorGateway actors, CampaignUnitOfWork transactions, CampaignReadRepository reads, CampaignPreparationService preparation) {
        this.actors=actors; this.transactions=transactions; this.reads=reads; this.preparation=preparation;
    }
    public PageResult<CampaignViews.Summary> search(UUID account,String text,String category,String location,CompensationType compensation,PageRequest page) {
        return transactions.execute(() -> new SearchCampaignsQueryHandler(reads).handle(new SearchCampaignsQuery(actors.getActiveCreator(account).id(),text,category,location,compensation,page)));
    }
    public CampaignViews.Details details(UUID account,CampaignId id) {
        return transactions.execute(() -> {
            if (actors.isBrand(account)) return preparation.own(account,id);
            actors.requireActiveCreator(account);
            return new GetCampaignDetailsQueryHandler(reads).handle(new GetCampaignDetailsQuery(id));
        });
    }
}
