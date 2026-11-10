package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class IncidentDomainTests {
    final Instant now=Instant.parse("2026-11-09T12:00:00Z");
    final UUID brand=UUID.randomUUID(),creator=UUID.randomUUID();
    Incident incident(){return Incident.open(UUID.randomUUID(),creator,Incident.Category.CONDITIONS,"Aclarar las condiciones",null,List.of(),now);}
    Incident.Terms keep(){return new Incident.Terms(Incident.Outcome.KEEP_AGREEMENT,"Continuar según lo acordado",List.of(),null,null,null,null);}
    Collaboration collaboration(){return new DeliveryDomainTests().collaboration();}

    @Test void resolutionNeedsTheOtherParticipantAndPreservesTheProposal() {
        var i=incident();i.propose(0,brand,keep(),now);
        var proposal=i.proposal();
        assertThrows(CollaborationFailure.class,()->i.decide(0,proposal.id(),brand,Incident.Decision.ACCEPT,null,now));
        assertEquals(Incident.Status.RESOLUTION_PROPOSED,i.status());
        i.decide(0,proposal.id(),creator,Incident.Decision.ACCEPT,null,now.plusSeconds(1));
        assertEquals(Incident.Status.RESOLVED,i.status());assertEquals(creator,i.proposal().decidedBy());
        assertEquals(proposal.terms(),i.proposal().terms());assertEquals(now.plusSeconds(1),i.resolvedAt());
        assertThrows(CollaborationFailure.class,()->i.propose(0,brand,keep(),now));
    }

    @Test void rejectingOrWithdrawingKeepsTheIncidentOpenAndDisallowsSelfConfirmation() {
        var i=incident();i.propose(0,creator,keep(),now);UUID first=i.proposal().id();
        assertThrows(IllegalArgumentException.class,()->i.decide(0,first,brand,Incident.Decision.REJECT," ",now));
        i.decide(0,first,brand,Incident.Decision.REJECT,"Debe aclararse el plazo",now.plusSeconds(1));
        assertEquals(Incident.Status.OPEN,i.status());assertEquals(Incident.ProposalStatus.REJECTED,i.proposal().status());
        i.propose(0,brand,keep(),now.plusSeconds(2));UUID second=i.proposal().id();assertNotEquals(first,second);
        assertThrows(CollaborationFailure.class,()->i.decide(0,second,creator,Incident.Decision.WITHDRAW,null,now));
        i.decide(0,second,brand,Incident.Decision.WITHDRAW,null,now.plusSeconds(3));
        assertEquals(Incident.Status.OPEN,i.status());assertNull(i.resolvedAt());
        assertThrows(CollaborationFailure.class,()->i.decide(0,first,creator,Incident.Decision.ACCEPT,null,now));
    }

    @Test void staleVersionsAndAnExpiredExtensionCannotCloseAnIncident() {
        var i=incident();i.restoreVersion(2);
        assertThrows(CollaborationFailure.class,()->i.propose(1,brand,keep(),now));
        var terms=new Incident.Terms(Incident.Outcome.EXTEND_CORRECTION,"Ampliar el plazo",List.of(),UUID.randomUUID(),UUID.randomUUID(),1L,now.plusSeconds(100));
        i.propose(2,brand,terms,now);
        assertThrows(CollaborationFailure.class,()->i.decide(2,i.proposal().id(),creator,Incident.Decision.ACCEPT,null,now.plusSeconds(100)));
        assertEquals(Incident.Status.RESOLUTION_PROPOSED,i.status());
    }

    @Test void sevenDaysAreMeasuredFromTheOriginalTerminalFactWithAnExclusiveLimit() {
        var c=collaboration();c.cancelByAgreement(now);c.refreshAfterIncidents(List.of(),0,now);
        var policy=new IncidentPolicy();
        assertTrue(policy.canOpen(c,now.plus(Duration.ofDays(7)).minusNanos(1)));
        assertFalse(policy.canOpen(c,now.plus(Duration.ofDays(7))));
        c.refreshAfterIncidents(List.of(),1,now.plus(Duration.ofDays(6)));
        assertEquals(Collaboration.Status.DISPUTED,c.status());
        assertEquals(now.plus(Duration.ofDays(7)),policy.closesAt(c));
        assertFalse(policy.canOpen(c,now.plus(Duration.ofDays(7))));
    }

    @Test void independentClaimsDoNotClearEachOthersBlocksAndDeadlinesKeepRunning() {
        var c=collaboration();c.refreshAfterIncidents(List.of(),2,now);
        assertThrows(CollaborationFailure.class,c::requireExecutionOpen);
        c.refreshAfterIncidents(List.of(),1,now.plusSeconds(200));
        assertEquals(Collaboration.Status.DISPUTED,c.status());assertEquals(1,c.openIncidentCount());
        c.refreshAfterIncidents(List.of(),0,now.plusSeconds(200));
        assertEquals(Collaboration.Status.EXPIRED,c.status());
    }

    @Test void completionDateSurvivesARecentDisputeAndCompletedWorkCannotBeCancelled() {
        var c=collaboration();var o=c.obligations().getFirst();var content=new DeliveryDomainTests().content();
        var a=Deliverable.submit(c.id(),o.id(),1,content,creator,o.deadline(),now);
        var b=Deliverable.submit(c.id(),o.id(),2,content,creator,o.deadline(),now);
        a.review(a.submission().id(),0,Deliverable.Decision.APPROVED,null,null,brand,now);
        b.review(b.submission().id(),0,Deliverable.Decision.APPROVED,null,null,brand,now);
        c.reconcile(List.of(a,b),now);
        c.refreshAfterIncidents(List.of(a,b),1,now.plusSeconds(100));
        assertEquals(Collaboration.Status.DISPUTED,c.status());assertEquals(now,c.completedAt());
        assertThrows(CollaborationFailure.class,()->c.cancelByAgreement(now.plusSeconds(101)));
        c.refreshAfterIncidents(List.of(a,b),0,now.plusSeconds(200));
        assertEquals(Collaboration.Status.COMPLETED,c.status());assertEquals(now,c.completedAt());
    }

    @Test void anAgreedExtensionDoesNotRewriteTheOriginalReviewOrItsTimeliness() {
        var c=collaboration();var o=c.obligations().getFirst();var content=new DeliveryDomainTests().content();
        var d=Deliverable.submit(c.id(),o.id(),1,content,creator,o.deadline(),now.plusSeconds(101));
        d.review(d.submission().id(),0,Deliverable.Decision.REJECTED,"Corregir el mensaje",now.plusSeconds(200),brand,now.plusSeconds(102));
        var original=d.review();
        d.extendCorrection(d.submission().id(),0,now.plusSeconds(500),now.plusSeconds(300));
        assertEquals(original,d.review());assertEquals(now.plusSeconds(500),d.effectiveCorrectionDeadline());
        d.revise(0,content,creator,o.deadline(),now.plusSeconds(499));
        assertEquals(2,d.submission().revision());assertTrue(d.submission().late());assertNull(d.correctionDeadlineOverride());
    }

    @Test void incidentInputsCannotContainEmptyDescriptionsOrNullEvidence() {
        assertThrows(IllegalArgumentException.class,()->Incident.open(UUID.randomUUID(),creator,Incident.Category.OTHER," ",null,List.of(),now));
        assertThrows(IllegalArgumentException.class,()->Incident.open(UUID.randomUUID(),creator,Incident.Category.OTHER,"Motivo",null,Arrays.asList((DeliveryContent.Reference)null),now));
        assertThrows(IllegalArgumentException.class,()->new Incident.Terms(Incident.Outcome.KEEP_AGREEMENT,"Mantener",List.of(),UUID.randomUUID(),null,null,null));
    }
}
