package com.collabtech.platform.campaign;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.campaign.domain.model.aggregates.*;
import com.collabtech.platform.campaign.domain.model.valueobjects.*;
import com.collabtech.platform.campaign.domain.exceptions.CampaignFailure;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CampaignEvaluationDomainTests {
    private Application pending(){return new Application(new ApplicationId(UUID.randomUUID()),new CampaignId(UUID.randomUUID()),new CreatorId(UUID.randomUUID()),"Propuesta",ApplicationStatus.PENDING,Instant.now());}
    @Test void metadataValidationIsAtomicAndCannotChangeIdentityOrPublish() {
        var campaign=Campaign.draft(new BrandId(UUID.randomUUID()),"Original","Objetivo",null,"Moda","Adultos","Lima");var id=campaign.id();
        assertThrows(IllegalArgumentException.class,()->campaign.updateMetadata("Nuevo"," ",null,null,null,null));
        assertEquals("Original",campaign.title());
        campaign.updateMetadata(" Nuevo ",null,"",null,null,null);
        assertEquals("Nuevo",campaign.title());assertEquals(id,campaign.id());assertEquals(CampaignStatus.DRAFT,campaign.status());
    }
    @Test void rejectionRequiresReasonAndVersionWithoutPartialMutation() {
        var a=pending();
        assertThrows(IllegalArgumentException.class,()->a.decide(ApplicationStatus.REJECTED,UUID.randomUUID()," ",Instant.now(),0));
        assertEquals(ApplicationStatus.PENDING,a.status());assertNull(a.decidedAt());
        assertThrows(CampaignFailure.class,()->a.decide(ApplicationStatus.SELECTED,UUID.randomUUID(),null,Instant.now(),1));
        assertEquals(ApplicationStatus.PENDING,a.status());
        var actor=UUID.randomUUID();var now=Instant.now();a.decide(ApplicationStatus.REJECTED,actor," Motivo ",now,0);
        assertEquals(actor,a.decidedBy());assertEquals(now,a.decidedAt());assertEquals("Motivo",a.decisionReason());
    }
    @ParameterizedTest @EnumSource(value=ApplicationStatus.class,names={"SELECTED","REJECTED","CANCELLED"})
    void terminalApplicationsCannotBeEditedCancelledOrDecidedAgain(ApplicationStatus state){
        var a=new Application(new ApplicationId(UUID.randomUUID()),new CampaignId(UUID.randomUUID()),new CreatorId(UUID.randomUUID()),"Message",state,Instant.now());
        assertThrows(CampaignFailure.class,()->a.updateMessage("New"));assertThrows(CampaignFailure.class,a::cancel);
        assertThrows(CampaignFailure.class,()->a.decide(ApplicationStatus.SELECTED,UUID.randomUUID(),null,Instant.now(),0));
    }
    @Test void onlySelectionAndRejectionAreEvaluationDecisions(){
        var a=pending();assertThrows(IllegalArgumentException.class,()->a.decide(ApplicationStatus.CANCELLED,UUID.randomUUID(),null,Instant.now(),0));
        assertEquals(ApplicationStatus.PENDING,a.status());
    }
}
