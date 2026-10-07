package com.collabtech.platform.campaign;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.campaign.domain.model.aggregates.Campaign;
import com.collabtech.platform.campaign.domain.model.entities.*;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import com.collabtech.platform.campaign.domain.events.CampaignPublished;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CampaignDomainTests {
    private final Instant now = Instant.parse("2026-10-05T12:00:00Z");
    private Campaign draft() { return Campaign.draft(new BrandId(UUID.randomUUID()), "Campaña", "Objetivo", null, "Moda", "Adultos", null); }
    private List<CampaignRequirement> requirements() { return List.of(new CampaignRequirement(new RequirementId(UUID.randomUUID()), "Afinidad con moda", true)); }
    private List<DeliverableSpecification> deliverables(Instant date) { return List.of(new DeliverableSpecification(new SpecificationId(UUID.randomUUID()), "VIDEO", "Video de producto", 1, date)); }
    private CompensationTerms cash() { return new CompensationTerms(CompensationType.CASH, new BigDecimal("500.00"), "pen", "Pago por contenido"); }
    @Test void draftIsIncompleteUntilAllConditionsAreDefined() {
        var campaign = draft();
        assertEquals(CampaignFailure.Code.INCOMPLETE_CAMPAIGN, assertThrows(CampaignFailure.class, () -> campaign.publish(now)).code());
        assertEquals(CampaignStatus.DRAFT, campaign.status()); assertNull(campaign.publicationDate());
    }
    @Test void validCampaignPublishesExactlyOnceWithInternalEvent() {
        var campaign = draft();
        campaign.defineConditions(requirements(), deliverables(now.plusSeconds(7200)), now.plusSeconds(3600), cash(), now);
        campaign.publish(now);
        assertEquals(CampaignStatus.OPEN, campaign.status()); assertEquals(now, campaign.publicationDate());
        var events = campaign.pullDomainEvents(); assertEquals(1, events.size()); assertInstanceOf(CampaignPublished.class, events.get(0));
        assertThrows(CampaignFailure.class, () -> campaign.publish(now));
        assertThrows(CampaignFailure.class, () -> campaign.defineConditions(requirements(), deliverables(now.plusSeconds(7200)), now.plusSeconds(3600), cash(), now));
        assertTrue(campaign.pullDomainEvents().isEmpty());
    }
    @Test void invalidDatesLeavePreviousConditionsUnchanged() {
        var campaign = draft(); var original = requirements();
        campaign.defineConditions(original, deliverables(now.plusSeconds(7200)), now.plusSeconds(3600), cash(), now);
        assertThrows(CampaignFailure.class, () -> campaign.defineConditions(requirements(), deliverables(now.plusSeconds(3600)), now.plusSeconds(3600), cash(), now));
        assertEquals(original, campaign.requirements()); assertEquals(now.plusSeconds(7200), campaign.deliverables().get(0).deadline());
    }
    @Test void closingRecordsOneEventPreservesConditionsAndCannotReopen() {
        var campaign=draft();
        assertEquals(CampaignFailure.Code.CAMPAIGN_NOT_OPEN,assertThrows(CampaignFailure.class,()->campaign.close(now)).code());
        campaign.defineConditions(requirements(),deliverables(now.plusSeconds(7200)),now.plusSeconds(3600),cash(),now);
        campaign.publish(now); campaign.pullDomainEvents(); var conditions=campaign.requirements();
        assertThrows(NullPointerException.class,()->campaign.close(null)); assertEquals(CampaignStatus.OPEN,campaign.status());
        campaign.close(now.plusSeconds(60)); campaign.close(now.plusSeconds(120));
        assertEquals(CampaignStatus.CLOSED,campaign.status()); assertFalse(campaign.acceptsApplications(now.plusSeconds(60)));
        assertEquals(conditions,campaign.requirements()); assertEquals(cash(),campaign.compensationTerms());
        var events=campaign.pullDomainEvents(); assertEquals(1,events.size());
        assertInstanceOf(com.collabtech.platform.campaign.domain.events.CampaignClosed.class,events.get(0));
        assertThrows(CampaignFailure.class,()->campaign.publish(now)); assertThrows(CampaignFailure.class,campaign::requireDiscardable);
    }
    @Test void expiredClosingDateIsRecheckedAtPublication() {
        var campaign = draft(); campaign.defineConditions(requirements(), deliverables(now.plusSeconds(7200)), now.plusSeconds(3600), cash(), now);
        assertThrows(CampaignFailure.class, () -> campaign.publish(now.plusSeconds(3600)));
        assertEquals(CampaignStatus.DRAFT, campaign.status());
    }
    @Test void emptyOrDuplicateChildIdentifiersCannotBeSaved() {
        var campaign = draft(); var item = requirements().get(0);
        assertThrows(CampaignFailure.class, () -> campaign.defineConditions(List.of(), deliverables(now.plusSeconds(7200)), now.plusSeconds(3600), cash(), now));
        assertThrows(CampaignFailure.class, () -> campaign.defineConditions(List.of(item,item), deliverables(now.plusSeconds(7200)), now.plusSeconds(3600), cash(), now));
        assertTrue(campaign.requirements().isEmpty());
    }
    @ParameterizedTest @EnumSource(value=CompensationType.class, names={"PRODUCT","SERVICE","CREDIT","BARTER"})
    void nonCashOffersAreDescriptiveAndDoNotAcceptCashAmounts(CompensationType type) {
        assertNull(new CompensationTerms(type, null, null, "Oferta definida").amount());
        assertThrows(IllegalArgumentException.class, () -> new CompensationTerms(type, new BigDecimal("10"), "PEN", "Oferta"));
    }
    @Test void cashAndContentInvariantsRejectInvalidValues() {
        assertEquals("PEN", cash().currency());
        assertThrows(IllegalArgumentException.class, () -> new CompensationTerms(CompensationType.CASH, BigDecimal.ZERO, "PEN", "Pago"));
        assertThrows(IllegalArgumentException.class, () -> new CompensationTerms(CompensationType.CASH, new BigDecimal("1.123"), "PEN", "Pago"));
        assertThrows(IllegalArgumentException.class, () -> new CompensationTerms(CompensationType.CASH, new BigDecimal("10"), "XYZ", "Pago"));
        assertThrows(IllegalArgumentException.class, () -> new DeliverableSpecification(new SpecificationId(UUID.randomUUID()), "VIDEO", "Video", 0, now));
        assertThrows(IllegalArgumentException.class, () -> Campaign.draft(new BrandId(UUID.randomUUID()), " ", "Objetivo", null, "Moda", "Audiencia", null));
    }
}
