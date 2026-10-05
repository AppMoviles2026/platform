package com.collabtech.platform.campaign.infrastructure.persistence.jpa;
import com.collabtech.platform.campaign.domain.repositories.ApplicationRepository;
import com.collabtech.platform.campaign.domain.model.aggregates.Application;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.application.ports.ApplicationReadRepository;
import com.collabtech.platform.campaign.application.queries.*;
import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.shared.application.pagination.PageResult;
import jakarta.persistence.*;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository @Profile("!skeleton") @Transactional
public class ApplicationPersistenceAdapter implements ApplicationRepository,ApplicationReadRepository {
    private final EntityManager em;
    public ApplicationPersistenceAdapter(EntityManager em) { this.em=em; }
    public Application save(Application application) {
        var row=em.find(ApplicationJpaEntity.class,application.id().value().toString());
        if (row==null) {
            row=new ApplicationJpaEntity(); row.id=application.id().value().toString();
            row.campaignId=application.campaignId().value().toString(); row.creatorId=application.creatorId().value().toString();
            row.submittedAt=application.submittedAt();
            row.confirmations=application.confirmations().stream().map(id -> id.value().toString()).collect(Collectors.toSet());
            row.message=application.message(); row.status=application.status().name(); em.persist(row);
        } else {
            if(row.version!=application.version()) throw new CampaignFailure(CampaignFailure.Code.CONCURRENT_UPDATE);
            row.message=application.message(); row.status=application.status().name(); em.lock(row,LockModeType.OPTIMISTIC);
        }
        em.flush(); application.restoreVersion(row.version); return application;
    }
    public Optional<Application> findById(ApplicationId id) { return Optional.ofNullable(em.find(ApplicationJpaEntity.class,id.value().toString())).map(this::domain); }
    public boolean existsByCampaignAndCreator(CampaignId campaign,CreatorId creator) {
        // A locking current read sees a prior submission even under MySQL REPEATABLE READ.
        // The caller first locks the campaign, serializing competing submissions to it.
        return !em.createQuery("select a from ApplicationJpaEntity a where a.campaignId=:campaign and a.creatorId=:creator",ApplicationJpaEntity.class)
            .setParameter("campaign",campaign.value().toString()).setParameter("creator",creator.value().toString())
            .setLockMode(LockModeType.PESSIMISTIC_READ).setMaxResults(1).getResultList().isEmpty();
    }
    public PageResult<CampaignViews.ApplicationView> findByCreator(GetCreatorApplicationsQuery query) {
        long offset=(long)query.page().page()*query.page().size(); if(offset>Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid page");
        var creator=query.creatorId().value().toString();
        var total=em.createQuery("select count(a) from ApplicationJpaEntity a where a.creatorId=:creator",Long.class).setParameter("creator",creator).getSingleResult();
        var rows=em.createQuery("select a from ApplicationJpaEntity a where a.creatorId=:creator order by a.submittedAt desc,a.id",ApplicationJpaEntity.class)
            .setParameter("creator",creator).setFirstResult((int)offset).setMaxResults(query.page().size()).getResultList();
        return new PageResult<>(rows.stream().map(this::view).toList(),total,query.page().page(),query.page().size());
    }
    public Optional<CampaignViews.ApplicationView> findDetails(GetApplicationDetailsQuery query) {
        var row=em.find(ApplicationJpaEntity.class,query.applicationId().value().toString());
        return row==null || !row.creatorId.equals(query.creatorId().value().toString()) ? Optional.empty():Optional.of(view(row));
    }
    private Application domain(ApplicationJpaEntity row) {
        var result=new Application(new ApplicationId(UUID.fromString(row.id)),new CampaignId(UUID.fromString(row.campaignId)),new CreatorId(UUID.fromString(row.creatorId)),
            row.message,ApplicationStatus.valueOf(row.status),row.submittedAt,row.confirmations.stream().map(id -> new RequirementId(UUID.fromString(id))).collect(Collectors.toSet()));
        result.restoreVersion(row.version); return result;
    }
    private CampaignViews.ApplicationView view(ApplicationJpaEntity row) {
        var campaign=em.find(CampaignJpaEntity.class,row.campaignId);
        return new CampaignViews.ApplicationView(UUID.fromString(row.id),UUID.fromString(row.campaignId),UUID.fromString(row.creatorId),campaign.title,campaign.brandName,
            row.message,row.status,row.submittedAt,row.confirmations.stream().map(UUID::fromString).collect(Collectors.toSet()),row.version);
    }
}
