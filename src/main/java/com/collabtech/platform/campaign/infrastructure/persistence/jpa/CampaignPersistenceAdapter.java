package com.collabtech.platform.campaign.infrastructure.persistence.jpa;

import com.collabtech.platform.campaign.application.ports.CampaignCatalog;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.repositories.CampaignRepository;
import com.collabtech.platform.shared.application.pagination.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Repository @Profile("!skeleton")
@Transactional
public class CampaignPersistenceAdapter implements CampaignRepository, CampaignCatalog {
    private final EntityManager em;
    public CampaignPersistenceAdapter(EntityManager em) { this.em = em; }
    public void saveNew(Campaign campaign, String brandName) {
        var row = new CampaignJpaEntity(); row.brandName = brandName;
        CampaignPersistenceMapper.copy(campaign, row); em.persist(row); em.flush(); campaign.restoreVersion(row.version);
    }
    public Campaign save(Campaign campaign) {
        var row = em.find(CampaignJpaEntity.class, campaign.id().value().toString());
        if (row == null || row.version != campaign.version()) throw new OptimisticLockException("Stale campaign");
        CampaignPersistenceMapper.copy(campaign, row);
        em.lock(row, LockModeType.OPTIMISTIC_FORCE_INCREMENT); em.flush();
        return campaign;
    }
    @Transactional(readOnly=true)
    public Optional<Campaign> findById(CampaignId id) {
        return Optional.ofNullable(em.find(CampaignJpaEntity.class, id.value().toString())).map(CampaignPersistenceMapper::toDomain);
    }
    public Optional<Campaign> findByIdForUpdate(CampaignId id) {
        return Optional.ofNullable(em.find(CampaignJpaEntity.class, id.value().toString(), LockModeType.PESSIMISTIC_WRITE)).map(CampaignPersistenceMapper::toDomain);
    }
    @Transactional(readOnly=true)
    public String brandName(CampaignId id) { return em.find(CampaignJpaEntity.class, id.value().toString()).brandName; }
    @Transactional(readOnly=true)
    public PageResult<Campaign> byBrand(BrandId brand, PageRequest page) { return page("c.brandId = :brand", brand.value().toString(), page); }
    @Transactional(readOnly=true)
    public PageResult<Campaign> published(PageRequest page) { return page("c.status = 'OPEN'", null, page); }
    private PageResult<Campaign> page(String predicate, String brand, PageRequest page) {
        var rows = em.createQuery("select c from CampaignJpaEntity c where " + predicate + " order by c.publicationDate desc, c.id", CampaignJpaEntity.class);
        var count = em.createQuery("select count(c) from CampaignJpaEntity c where " + predicate, Long.class);
        if (brand != null) { rows.setParameter("brand", brand); count.setParameter("brand", brand); }
        long offset = (long) page.page() * page.size();
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("Page offset too large");
        return new PageResult<>(rows.setFirstResult((int) offset).setMaxResults(page.size()).getResultList().stream().map(CampaignPersistenceMapper::toDomain).toList(), count.getSingleResult(), page.page(), page.size());
    }
}
