package com.collabtech.platform.campaign.application.services;
import com.collabtech.platform.campaign.application.ports.*;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.application.queries.GetApplicationDetailsQuery;
import com.collabtech.platform.campaign.domain.repositories.*;
import com.collabtech.platform.campaign.domain.model.aggregates.Application;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Clock;
import java.util.UUID;

public final class ApplicationEvaluationService {
    private final CampaignActorGateway actors;
    private final CampaignUnitOfWork work;
    private final CampaignRepository campaigns;
    private final ApplicationRepository applications;
    private final ApplicationReadRepository reads;
    private final ApplicantProfiles profiles;
    private final Clock clock;
    public ApplicationEvaluationService(CampaignActorGateway actors,CampaignUnitOfWork work,CampaignRepository campaigns,
            ApplicationRepository applications,ApplicationReadRepository reads,ApplicantProfiles profiles,Clock clock){
        this.actors=actors;this.work=work;this.campaigns=campaigns;this.applications=applications;this.reads=reads;this.profiles=profiles;this.clock=clock;
    }
    public record Candidate(CampaignViews.ApplicationView application,ApplicantProfiles.Profile creator) {}
    public PageResult<Candidate> list(UUID actor,CampaignId campaign,ApplicationStatus status,PageRequest page){
        return work.execute(()->{own(actor,campaign);var data=reads.byCampaign(campaign,status,page);
            return new PageResult<>(data.items().stream().map(v->new Candidate(v,profiles.find(v.creatorId()).orElse(null))).toList(),data.total(),data.page(),data.size());});
    }
    public Candidate get(UUID actor,CampaignId campaign,ApplicationId id){
        return work.execute(()->{own(actor,campaign);return view(application(campaign,id));});
    }
    public Candidate decide(UUID actor,CampaignId campaign,ApplicationId id,ApplicationStatus decision,String reason,long version){
        return work.execute(()->{
            own(actor,campaign);
            var application=application(campaign,id);
            application.decide(decision,actor,reason,clock.instant(),version);
            applications.save(application);return view(application);
        });
    }
    private void own(UUID actor,CampaignId id){
        var brand=actors.getActiveBrand(actor);
        var campaign=campaigns.findById(id).orElseThrow(()->new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_FOUND));
        if(!campaign.brandId().equals(brand.id()))throw new CampaignFailure(CampaignFailure.Code.FORBIDDEN);
    }
    private Application application(CampaignId campaign,ApplicationId id){
        var a=applications.findById(id).orElseThrow(()->new CampaignFailure(CampaignFailure.Code.APPLICATION_NOT_FOUND));
        if(!a.campaignId().equals(campaign))throw new CampaignFailure(CampaignFailure.Code.APPLICATION_NOT_FOUND);
        return a;
    }
    private Candidate view(Application a){
        var v=reads.findDetails(new GetApplicationDetailsQuery(a.creatorId(),a.id())).orElseThrow(()->new CampaignFailure(CampaignFailure.Code.APPLICATION_NOT_FOUND));
        return new Candidate(v,profiles.find(a.creatorId().value()).orElse(null));
    }
}
