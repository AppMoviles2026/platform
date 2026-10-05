package com.collabtech.platform.campaign.infrastructure.persistence.jpa;

import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.entities.*;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import java.util.UUID;

final class CampaignPersistenceMapper {
    private CampaignPersistenceMapper() {}
    static Campaign toDomain(CampaignJpaEntity row) {
        var compensation = row.compensationType == null ? null : new CompensationTerms(CompensationType.valueOf(row.compensationType), row.amount, row.currency, row.compensationDescription);
        var result = new Campaign(new CampaignId(UUID.fromString(row.id)), new BrandId(UUID.fromString(row.brandId)), row.title, row.objective,
                row.description, row.category, row.audience, row.location, CampaignStatus.valueOf(row.status), row.publicationDate, row.applicationDeadline,
                row.requirements.stream().map(item -> new CampaignRequirement(new RequirementId(UUID.fromString(item.id)), item.description, item.mandatory)).toList(),
                row.deliverables.stream().map(item -> new DeliverableSpecification(new SpecificationId(UUID.fromString(item.id)), item.contentType, item.description, item.quantity, item.deadline)).toList(), compensation);
        result.restoreVersion(row.version); return result;
    }
    static void copy(Campaign campaign, CampaignJpaEntity row) {
        row.id = campaign.id().value().toString(); row.brandId = campaign.brandId().value().toString();
        row.title = campaign.title(); row.objective = campaign.objective(); row.description = campaign.description();
        row.category = campaign.category(); row.audience = campaign.targetAudience(); row.location = campaign.location();
        row.status = campaign.status().name(); row.publicationDate = campaign.publicationDate(); row.applicationDeadline = campaign.applicationDeadline();
        var offer = campaign.compensationTerms();
        row.compensationType = offer == null ? null : offer.type().name(); row.amount = offer == null ? null : offer.amount();
        row.currency = offer == null ? null : offer.currency(); row.compensationDescription = offer == null ? null : offer.description();
        row.requirements.removeIf(item -> campaign.requirements().stream().noneMatch(value -> value.id().value().toString().equals(item.id)));
        for (var item : campaign.requirements()) {
            var entity = row.requirements.stream().filter(value -> value.id.equals(item.id().value().toString())).findFirst().orElseGet(() -> {
                var added = new CampaignRequirementJpaEntity(); added.id = item.id().value().toString(); added.campaign = row; row.requirements.add(added); return added;
            });
            entity.description = item.description(); entity.mandatory = item.mandatory();
        }
        row.deliverables.removeIf(item -> campaign.deliverables().stream().noneMatch(value -> value.id().value().toString().equals(item.id)));
        for (var item : campaign.deliverables()) {
            var entity = row.deliverables.stream().filter(value -> value.id.equals(item.id().value().toString())).findFirst().orElseGet(() -> {
                var added = new DeliverableSpecificationJpaEntity(); added.id = item.id().value().toString(); added.campaign = row; row.deliverables.add(added); return added;
            });
            entity.contentType = item.contentType(); entity.description = item.description(); entity.quantity = item.quantity(); entity.deadline = item.deadline();
        }
    }
}
