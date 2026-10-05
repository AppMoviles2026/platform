package com.collabtech.platform.campaign.interfaces.rest.resources;

import com.collabtech.platform.campaign.application.projections.CampaignViews;
import com.collabtech.platform.campaign.domain.model.valueobjects.CompensationTerms;
import com.collabtech.platform.shared.application.pagination.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CampaignResources {
    private CampaignResources() {}
    public record Compensation(String type, BigDecimal amount, String currency, String description) {}
    public record Summary(UUID id, UUID brandId, String brandName, String title, String category, String location,
            Compensation compensation, Instant applicationDeadline, String status) {}
    public record Requirement(UUID id, String description, boolean mandatory) {}
    public record Deliverable(UUID id, String contentType, String description, int quantity, Instant deadline) {}
    public record Details(UUID id, UUID brandId, String brandName, String title, String category, String location,
            String objective, String description, String targetAudience, Compensation compensation,
            Instant applicationDeadline, String status, Instant publicationDate, List<Requirement> requirements,
            List<Deliverable> deliverables, boolean acceptsApplications) {}
    private static Compensation compensation(CompensationTerms value) {
        return value == null ? null : new Compensation(value.type().name(), value.amount(), value.currency(), value.description());
    }
    public static Summary summary(CampaignViews.Summary view) {
        return new Summary(view.id(), view.brandId(), view.brandName(), view.title(), view.category(), view.location(),
                compensation(view.compensation()), view.applicationDeadline(), view.status());
    }
    public static PageResult<Summary> page(PageResult<CampaignViews.Summary> view) {
        return new PageResult<>(view.items().stream().map(CampaignResources::summary).toList(), view.total(), view.page(), view.size());
    }
    public static Details details(CampaignViews.Details view) {
        var info = view.summary();
        return new Details(info.id(), info.brandId(), info.brandName(), info.title(), info.category(), info.location(),
                view.objective(), view.description(), view.targetAudience(), compensation(info.compensation()), info.applicationDeadline(),
                info.status(), view.publicationDate(), view.requirements().stream().map(item -> new Requirement(item.id(), item.description(), item.mandatory())).toList(),
                view.deliverables().stream().map(item -> new Deliverable(item.id(), item.contentType(), item.description(), item.quantity(), item.deadline())).toList(), view.acceptsApplications());
    }
}
