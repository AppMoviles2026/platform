package com.collabtech.platform.campaign.domain.model.aggregates;

import com.collabtech.platform.shared.domain.model.AggregateRoot;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignId;
import com.collabtech.platform.campaign.domain.model.valueobjects.BrandId;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignStatus;
import com.collabtech.platform.campaign.domain.model.valueobjects.CompensationTerms;
import com.collabtech.platform.campaign.domain.model.entities.CampaignRequirement;
import com.collabtech.platform.campaign.domain.model.entities.DeliverableSpecification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import com.collabtech.platform.campaign.domain.model.valueobjects.CampaignText;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.campaign.domain.events.CampaignPublished;

/** Aggregate owning campaign preparation and publication, including all its conditions. */
public final class Campaign extends AggregateRoot<CampaignId> {
    private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59Z");
    private final BrandId brandId;
    private final String title;
    private final String objective;
    private final String description;
    private final String category;
    private final String targetAudience;
    private final String location;
    private CampaignStatus status;
    private Instant publicationDate;
    private Instant applicationDeadline;
    private List<CampaignRequirement> requirements;
    private List<DeliverableSpecification> deliverables;
    private CompensationTerms compensationTerms;
    private long version;

    public Campaign(CampaignId id, BrandId brandId, String title, String objective, String description, String category, String targetAudience, String location, CampaignStatus status, Instant publicationDate, Instant applicationDeadline, List<CampaignRequirement> requirements, List<DeliverableSpecification> deliverables, CompensationTerms compensationTerms) {
        super(id);
        this.brandId = Objects.requireNonNull(brandId);
        this.title = CampaignText.required(title, 200);
        this.objective = CampaignText.required(objective, 2000);
        this.description = CampaignText.optional(description, 5000);
        this.category = CampaignText.required(category, 100);
        this.targetAudience = CampaignText.required(targetAudience, 2000);
        this.location = CampaignText.optional(location, 150);
        this.status = Objects.requireNonNull(status);
        this.publicationDate = publicationDate;
        this.applicationDeadline = applicationDeadline;
        this.requirements = List.copyOf(requirements);
        this.deliverables = List.copyOf(deliverables);
        this.compensationTerms = compensationTerms;
    }

    public BrandId brandId() { return brandId; }
    public String title() { return title; }
    public String objective() { return objective; }
    public String description() { return description; }
    public String category() { return category; }
    public String targetAudience() { return targetAudience; }
    public String location() { return location; }
    public CampaignStatus status() { return status; }
    public Instant publicationDate() { return publicationDate; }
    public Instant applicationDeadline() { return applicationDeadline; }
    public List<CampaignRequirement> requirements() { return requirements; }
    public List<DeliverableSpecification> deliverables() { return deliverables; }
    public CompensationTerms compensationTerms() { return compensationTerms; }
    public long version() { return version; }
    public void restoreVersion(long version) { if (version < 0) throw new IllegalArgumentException("Invalid version"); this.version = version; }

    public static Campaign draft(BrandId brand, String title, String objective, String description, String category, String audience, String location) {
        return new Campaign(new CampaignId(UUID.randomUUID()), brand, title, objective, description, category, audience, location,
                CampaignStatus.DRAFT, null, null, List.of(), List.of(), null);
    }
    public void defineConditions(List<CampaignRequirement> requirements, List<DeliverableSpecification> deliverables,
            Instant applicationDeadline, CompensationTerms compensation, Instant now) {
        requireDraft();
        validateConditions(requirements, deliverables, applicationDeadline, compensation, now);
        // Validate every field before changing any part of the aggregate.
        this.requirements = List.copyOf(requirements); this.deliverables = List.copyOf(deliverables);
        this.applicationDeadline = applicationDeadline; this.compensationTerms = compensation;
    }
    public void publish(Instant now) {
        requireDraft();
        if (compensationTerms == null || applicationDeadline == null || requirements.isEmpty() || deliverables.isEmpty())
            throw new CampaignFailure(CampaignFailure.Code.INCOMPLETE_CAMPAIGN);
        validateConditions(requirements, deliverables, applicationDeadline, compensationTerms, now);
        status = CampaignStatus.OPEN; publicationDate = now;
        recordEvent(new CampaignPublished(UUID.randomUUID(), now, id()));
    }
    private void requireDraft() {
        if (status != CampaignStatus.DRAFT) throw new CampaignFailure(CampaignFailure.Code.CAMPAIGN_NOT_DRAFT);
    }
    private static void validateConditions(List<CampaignRequirement> requirements, List<DeliverableSpecification> deliverables,
            Instant deadline, CompensationTerms compensation, Instant now) {
        boolean valid = requirements != null && !requirements.isEmpty() && requirements.size() <= 50
                && deliverables != null && !deliverables.isEmpty() && deliverables.size() <= 50
                && deadline != null && now != null && deadline.isAfter(now) && !deadline.isAfter(MAX_DATE) && compensation != null;
        if (!valid || requirements.stream().anyMatch(Objects::isNull) || deliverables.stream().anyMatch(Objects::isNull)
                || requirements.stream().map(CampaignRequirement::id).distinct().count() != requirements.size()
                || deliverables.stream().map(DeliverableSpecification::id).distinct().count() != deliverables.size()
                || deliverables.stream().anyMatch(item -> !item.deadline().isAfter(deadline) || item.deadline().isAfter(MAX_DATE))) {
            throw new CampaignFailure(CampaignFailure.Code.INVALID_CONDITIONS);
        }
    }
}
