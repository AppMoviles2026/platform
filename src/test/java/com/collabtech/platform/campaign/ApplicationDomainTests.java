package com.collabtech.platform.campaign;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.campaign.domain.model.aggregates.*;
import com.collabtech.platform.campaign.domain.model.entities.*;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.services.*;
import com.collabtech.platform.campaign.domain.services.ApplicationEligibilityService.EligibilityFacts;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
class ApplicationDomainTests {
    private final Instant now=Instant.parse("2026-10-05T12:00:00Z");
    private Campaign campaign(RequirementRule rule,boolean mandatory) {
        var campaign=Campaign.draft(new BrandId(UUID.randomUUID()),"Campaña","Objetivo",null,"Moda","Adultos","Lima");
        campaign.defineConditions(List.of(new CampaignRequirement(new RequirementId(UUID.randomUUID()),"Requisito",mandatory,rule)),
            List.of(new DeliverableSpecification(new SpecificationId(UUID.randomUUID()),"VIDEO","Video",1,now.plusSeconds(7200))),
            now.plusSeconds(3600),new CompensationTerms(CompensationType.PRODUCT,null,null,"Producto"),now);
        campaign.publish(now); return campaign;
    }
    @Test void allExplicitAutomaticRulesCanBeSatisfiedWithoutMetrics() {
        var service=new ExplicitApplicationEligibilityService();
        for(var rule:List.of(new RequirementRule(RequirementRule.Type.NICHE_EQUALS,"moda"),new RequirementRule(RequirementRule.Type.LOCATION_EQUALS,"lima"),new RequirementRule(RequirementRule.Type.AUTHORIZED_PLATFORM,"INSTAGRAM"))) {
            var campaign=campaign(rule,true);
            assertTrue(service.evaluate(campaign,new EligibilityFacts("Moda","Lima","texto libre",Set.of("instagram"),Set.of())).eligible());
            assertFalse(service.evaluate(campaign,new EligibilityFacts(null,null,"100000 seguidores",Set.of(),Set.of())).eligible());
        }
    }
    @Test void manualConfirmationIsExplicitAndOptionalRulesNeverBlock() {
        var campaign=campaign(RequirementRule.manual(),true); var service=new ExplicitApplicationEligibilityService();
        assertFalse(service.evaluate(campaign,new EligibilityFacts(null,null,null,Set.of(),Set.of())).eligible());
        assertTrue(service.evaluate(campaign,new EligibilityFacts(null,null,null,Set.of(),Set.of(campaign.requirements().get(0).id()))).eligible());
        assertTrue(service.evaluate(campaign(RequirementRule.manual(),false),new EligibilityFacts(null,null,null,Set.of(),Set.of())).eligible());
    }
    @Test void submittedAggregateRecordsEventAndPendingTransitions() {
        var campaign=campaign(RequirementRule.manual(),false);
        var application=Application.submit(campaign,new CreatorId(UUID.randomUUID())," Propuesta ",Set.of(),now);
        assertEquals(ApplicationStatus.PENDING,application.status()); assertEquals("Propuesta",application.message());
        assertEquals(1,application.pullDomainEvents().size());
        application.updateMessage("Actualizada"); application.cancel(); assertEquals(ApplicationStatus.CANCELLED,application.status());
        assertThrows(CampaignFailure.class,() -> application.updateMessage("No")); assertThrows(CampaignFailure.class,application::cancel);
    }
    @Test void deadlineIsExclusiveAndFactoryRejectsExpiredCampaign() {
        var campaign=campaign(RequirementRule.manual(),false);
        assertTrue(campaign.acceptsApplications(now.plusSeconds(3599))); assertFalse(campaign.acceptsApplications(now.plusSeconds(3600)));
        assertThrows(CampaignFailure.class,() -> Application.submit(campaign,new CreatorId(UUID.randomUUID()),"Propuesta",Set.of(),now.plusSeconds(3600)));
    }
    @ParameterizedTest @ValueSource(strings={"SELECTED","REJECTED","CANCELLED"})
    void everyTerminalStateBlocksModification(String state) {
        var application=new Application(new ApplicationId(UUID.randomUUID()),new CampaignId(UUID.randomUUID()),new CreatorId(UUID.randomUUID()),"Propuesta",ApplicationStatus.valueOf(state),now);
        assertThrows(CampaignFailure.class,() -> application.updateMessage("Cambio")); assertThrows(CampaignFailure.class,application::cancel);
    }
    @Test void invalidRuleSpecificationsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new RequirementRule(RequirementRule.Type.NICHE_EQUALS," "));
        assertThrows(IllegalArgumentException.class,() -> new RequirementRule(RequirementRule.Type.AUTHORIZED_PLATFORM,"facebook"));
        assertThrows(IllegalArgumentException.class,() -> new RequirementRule(RequirementRule.Type.MANUAL_CONFIRMATION,"1000"));
        assertThrows(IllegalArgumentException.class,() -> new RequirementRule(null,null));
    }
}

