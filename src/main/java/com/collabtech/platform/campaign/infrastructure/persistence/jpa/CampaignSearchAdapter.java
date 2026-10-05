package com.collabtech.platform.campaign.infrastructure.persistence.jpa;
import com.collabtech.platform.campaign.application.ports.*;
import com.collabtech.platform.campaign.application.queries.*;
import com.collabtech.platform.campaign.application.projections.*;
import com.collabtech.platform.shared.application.pagination.PageResult;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Locale;
import java.time.Clock;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Repository @Profile("!skeleton") @Transactional(readOnly=true)
public class CampaignSearchAdapter implements CampaignReadRepository {
    private final EntityManager em; private final CampaignCatalog catalog; private final Clock clock;
    public CampaignSearchAdapter(EntityManager em, CampaignCatalog catalog, Clock clock) { this.em = em; this.catalog = catalog; this.clock = clock; }
    public PageResult<CampaignViews.Summary> search(SearchCampaignsQuery query) {
        var where = new StringBuilder("c.status = 'OPEN'"); var params = new LinkedHashMap<String,Object>();
        if (query.text() != null) {
            where.append(" and (lower(c.title) like :text escape '!' or lower(c.brandName) like :text escape '!' or lower(c.objective) like :text escape '!')");
            params.put("text", like(query.text()));
        }
        if (query.category() != null) { where.append(" and lower(c.category) = :category"); params.put("category", query.category().toLowerCase(Locale.ROOT)); }
        if (query.location() != null) { where.append(" and lower(c.location) like :location escape '!'"); params.put("location", like(query.location())); }
        if (query.compensationType() != null) { where.append(" and c.compensationType = :compensation"); params.put("compensation", query.compensationType().name()); }
        var rows = em.createQuery("select c from CampaignJpaEntity c where " + where + " order by c.publicationDate desc,c.id", CampaignJpaEntity.class);
        var count = em.createQuery("select count(c) from CampaignJpaEntity c where " + where, Long.class);
        params.forEach((name,value) -> { rows.setParameter(name,value); count.setParameter(name,value); });
        long offset = (long)query.page().page() * query.page().size();
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("Page offset too large");
        var items = rows.setFirstResult((int)offset).setMaxResults(query.page().size()).getResultList().stream()
                .map(row -> CampaignViewMapper.summary(CampaignPersistenceMapper.toDomain(row),row.brandName)).toList();
        return new PageResult<>(items,count.getSingleResult(),query.page().page(),query.page().size());
    }
    public PageResult<CampaignViews.Summary> findByBrand(GetBrandCampaignsQuery query) {
        var result = catalog.byBrand(query.brandId(),query.page());
        return new PageResult<>(result.items().stream().map(item -> CampaignViewMapper.summary(item,catalog.brandName(item.id()))).toList(), result.total(), result.page(), result.size());
    }
    public Optional<CampaignViews.Details> findDetails(GetCampaignDetailsQuery query) {
        return Optional.ofNullable(em.find(CampaignJpaEntity.class,query.campaignId().value().toString()))
                .map(row -> CampaignViewMapper.details(CampaignPersistenceMapper.toDomain(row),row.brandName,clock.instant()));
    }
    private static String like(String value) { return "%" + value.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_") + "%"; }
}
