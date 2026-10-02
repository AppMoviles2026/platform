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

/** Structural model only. Business transitions are scheduled in docs/implementation-plan.md. */
public final class Campaign extends AggregateRoot<CampaignId> {
    private final BrandId brandId;
    private final String title;
    private final String objective;
    private final String description;
    private final String category;
    private final String targetAudience;
    private final String location;
    private final CampaignStatus status;
    private final Instant publicationDate;
    private final Instant applicationDeadline;
    private final List<CampaignRequirement> requirements;
    private final List<DeliverableSpecification> deliverables;
    private final CompensationTerms compensationTerms;

    public Campaign(CampaignId id, BrandId brandId, String title, String objective, String description, String category, String targetAudience, String location, CampaignStatus status, Instant publicationDate, Instant applicationDeadline, List<CampaignRequirement> requirements, List<DeliverableSpecification> deliverables, CompensationTerms compensationTerms) {
        super(id);
        this.brandId = brandId;
        this.title = title;
        this.objective = objective;
        this.description = description;
        this.category = category;
        this.targetAudience = targetAudience;
        this.location = location;
        this.status = status;
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
}
