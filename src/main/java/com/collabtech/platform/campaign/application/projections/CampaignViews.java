package com.collabtech.platform.campaign.application.projections;

import com.collabtech.platform.campaign.domain.model.valueobjects.CompensationTerms;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Structured projection contracts for future REST resources and Android view-models. */
public final class CampaignViews {
    private CampaignViews() {}
    public record Summary(UUID id, UUID brandId, String brandName, String title, String category,
                          String location, CompensationTerms compensation, Instant applicationDeadline, String status) {}
    public record Details(Summary summary, String objective, String description, String targetAudience,
                          List<Requirement> requirements, List<Deliverable> deliverables, boolean acceptsApplications, Instant publicationDate) {
        public Details { requirements = List.copyOf(requirements); deliverables = List.copyOf(deliverables); }
    }
    public record Requirement(UUID id, String description, boolean mandatory) {}
    public record Deliverable(UUID id, String contentType, String description, int quantity, Instant deadline) {}
    public record ApplicationView(UUID id, UUID campaignId, UUID creatorId, String campaignTitle,
                                  String brandName, String message, String status, Instant submittedAt) {}
}
