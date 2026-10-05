package com.collabtech.platform.campaign.interfaces.rest.resources;

import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.domain.model.valueobjects.CompensationType;
import com.collabtech.platform.campaign.domain.model.valueobjects.CompensationTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CampaignRequests {
    private CampaignRequests() {}
    public record Create(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=2000) String objective,
            @Size(max=5000) String description, @NotBlank @Size(max=100) String category,
            @NotBlank @Size(max=2000) String targetAudience, @Size(max=150) String location) {}
    public record Requirement(@NotBlank @Size(max=2000) String description, @NotNull Boolean mandatory) {
        public CampaignViews.Requirement view() { return new CampaignViews.Requirement(UUID.randomUUID(), description, mandatory); }
    }
    public record Deliverable(@NotBlank @Size(max=100) String contentType, @NotBlank @Size(max=2000) String description,
            @NotNull @Min(1) @Max(1000) Integer quantity, @NotNull Instant deadline) {
        public CampaignViews.Deliverable view() { return new CampaignViews.Deliverable(UUID.randomUUID(), contentType, description, quantity, deadline); }
    }
    public record Compensation(@NotNull CompensationType type, @Positive @Digits(integer=10, fraction=2) BigDecimal amount,
            @Size(min=3,max=3) String currency, @NotBlank @Size(max=2000) String description) {
        public CompensationTerms value() { return new CompensationTerms(type, amount, currency, description); }
    }
    public record Conditions(@NotNull @Size(min=1,max=50) List<@NotNull @Valid Requirement> requirements,
            @NotNull @Size(min=1,max=50) List<@NotNull @Valid Deliverable> deliverables,
            @NotNull Instant applicationDeadline, @NotNull @Valid Compensation compensation) {}
}
