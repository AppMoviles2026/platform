package com.collabtech.platform.collaboration;
import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AgreementDomainTests {
    final Instant now=Instant.parse("2026-11-09T12:00:00Z");
    AgreementSnapshot terms(){return new AgreementSnapshot(UUID.randomUUID(),UUID.randomUUID(),1,now,UUID.randomUUID(),3,UUID.randomUUID(),UUID.randomUUID(),"Marca","Creador","Campaña","Objetivo",null,"Food","Audiencia","Lima","Propuesta",Set.of(),List.of(),
            List.of(new AgreementSnapshot.DeliverableTerm(UUID.randomUUID(),"VIDEO","Dos videos",2,now.plusSeconds(100))),
            new AgreementSnapshot.Compensation("PRODUCT",null,null,"Canje"),now.minusSeconds(10),now,now.plusSeconds(100));}
    @Test void aSelectionAndOneAcknowledgementDoNotActivateCollaboration(){var a=Agreement.propose(terms(),now);var brand=UUID.randomUUID();
        assertThrows(CollaborationFailure.class,()->Collaboration.start(a));a.decide(Agreement.Party.BRAND,brand,a.snapshot().termsVersion(),Agreement.Decision.ACCEPT,now);
        assertEquals(Agreement.Status.PENDING,a.status());assertThrows(CollaborationFailure.class,()->Collaboration.start(a));
        a.decide(Agreement.Party.BRAND,brand,a.snapshot().termsVersion(),Agreement.Decision.ACCEPT,now);assertNull(a.creatorAcceptedAt());
        assertThrows(CollaborationFailure.class,()->a.decide(Agreement.Party.BRAND,brand,a.snapshot().termsVersion(),Agreement.Decision.REJECT,now));
        a.decide(Agreement.Party.CREATOR,UUID.randomUUID(),a.snapshot().termsVersion(),Agreement.Decision.ACCEPT,now.plusSeconds(1));
        var c=Collaboration.start(a);assertEquals(Agreement.Status.ACCEPTED,a.status());assertEquals(2,c.obligations().getFirst().quantity());assertEquals(0,c.obligations().getFirst().approvedQuantity());}
    @ParameterizedTest @EnumSource(Agreement.Party.class)
    void rejectionByEitherPartyPreventsActivation(Agreement.Party party){var a=Agreement.propose(terms(),now);var actor=UUID.randomUUID();
        a.decide(party,actor,a.snapshot().termsVersion(),Agreement.Decision.REJECT,now);a.decide(party,actor,a.snapshot().termsVersion(),Agreement.Decision.REJECT,now);
        assertEquals(Agreement.Status.REJECTED,a.status());assertThrows(CollaborationFailure.class,()->Collaboration.start(a));
        assertThrows(CollaborationFailure.class,()->a.decide(party,actor,a.snapshot().termsVersion(),Agreement.Decision.ACCEPT,now));}
    @Test void firstDeliveryIsExclusiveAcceptanceBoundaryAndClosedIntakeDoesNotMatter(){var s=terms();var a=Agreement.propose(s,now);
        assertThrows(CollaborationFailure.class,()->Agreement.propose(s,s.acceptanceDeadline()));
        assertThrows(CollaborationFailure.class,()->a.decide(Agreement.Party.BRAND,UUID.randomUUID(),s.termsVersion(),Agreement.Decision.ACCEPT,s.acceptanceDeadline()));
        assertEquals(Agreement.Status.EXPIRED,a.effectiveStatus(s.acceptanceDeadline()));assertTrue(a.expire(s.acceptanceDeadline()));assertFalse(a.expire(now.plusSeconds(200)));}
    @Test void oldTermsCannotBeAcceptedAndSnapshotsAreImmutable(){var a=Agreement.propose(terms(),now);
        assertThrows(CollaborationFailure.class,()->a.decide(Agreement.Party.BRAND,UUID.randomUUID(),UUID.randomUUID(),Agreement.Decision.ACCEPT,now));
        assertNull(a.brandAcceptedAt());assertThrows(UnsupportedOperationException.class,()->a.snapshot().deliverables().clear());}
    @Test void expiryKeepsUnfulfilledQuantitiesForLateDelivery(){var a=Agreement.propose(terms(),now);
        a.decide(Agreement.Party.BRAND,UUID.randomUUID(),a.snapshot().termsVersion(),Agreement.Decision.ACCEPT,now);
        a.decide(Agreement.Party.CREATOR,UUID.randomUUID(),a.snapshot().termsVersion(),Agreement.Decision.ACCEPT,now);
        var c=Collaboration.start(a);assertEquals(Collaboration.Status.ACTIVE,c.effectiveStatus(now));
        assertEquals(Collaboration.Status.EXPIRED,c.effectiveStatus(now.plusSeconds(100)));assertTrue(c.expire(now.plusSeconds(100)));assertFalse(c.expire(now.plusSeconds(101)));
        assertEquals(2,c.obligations().getFirst().quantity());assertEquals(0,c.obligations().getFirst().approvedQuantity());}
}
