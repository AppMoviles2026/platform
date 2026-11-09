package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.infrastructure.files.DeliveryFileInspector;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeliveryDomainTests {
    final Instant now=Instant.parse("2026-11-09T12:00:00Z");
    DeliveryContent content() {
        return new DeliveryContent(new DeliveryContent.Reference("https://example.com/content",null),
                List.of(new DeliveryContent.Reference("https://example.com/proof",null)),"Contenido publicado");
    }
    Collaboration collaboration() {
        var terms=new AgreementDomainTests().terms();
        var agreement=Agreement.propose(terms,now);
        agreement.decide(Agreement.Party.BRAND,UUID.randomUUID(),terms.termsVersion(),Agreement.Decision.ACCEPT,now);
        agreement.decide(Agreement.Party.CREATOR,UUID.randomUUID(),terms.termsVersion(),Agreement.Decision.ACCEPT,now);
        return Collaboration.start(agreement);
    }
    @Test void pendingReviewDoesNotMissTheDeliveryDeadlineAndOnlyAllApprovedQuantitiesComplete() {
        var c=collaboration();var o=c.obligations().getFirst();var creator=UUID.randomUUID();var brand=UUID.randomUUID();
        var first=Deliverable.submit(c.id(),o.id(),1,content(),creator,o.deadline(),now);
        c.reconcile(List.of(first),now);assertEquals(Collaboration.Status.UNDER_REVIEW,c.status());assertEquals(o.deadline(),c.nextDeadline());
        var second=Deliverable.submit(c.id(),o.id(),2,content(),creator,o.deadline(),now);
        c.reconcile(List.of(first,second),now);assertNull(c.nextDeadline());
        assertEquals(Collaboration.Status.UNDER_REVIEW,c.effectiveStatus(now.plusSeconds(1000)));
        first.review(first.submission().id(),0,Deliverable.Decision.APPROVED,null,null,brand,now.plusSeconds(200));
        c.reconcile(List.of(first,second),now.plusSeconds(200));assertEquals(1,c.obligations().getFirst().approvedQuantity());
        assertNull(c.completedAt());
        second.review(second.submission().id(),0,Deliverable.Decision.APPROVED,null,null,brand,now.plusSeconds(200));
        c.reconcile(List.of(first,second),now.plusSeconds(200));assertEquals(Collaboration.Status.COMPLETED,c.status());
        assertEquals(now.plusSeconds(200),c.completedAt());assertThrows(CollaborationFailure.class,c::requireExecutionOpen);
    }
    @Test void correctionKeepsOriginalTimelinessAndCannotBeSubmittedAtTheNewDeadline() {
        var c=collaboration();var o=c.obligations().getFirst();var d=Deliverable.submit(c.id(),o.id(),1,content(),UUID.randomUUID(),o.deadline(),now.plusSeconds(101));
        assertTrue(d.submission().late());var old=d.submission();var correction=now.plusSeconds(500);
        d.review(old.id(),0,Deliverable.Decision.REJECTED,"Falta el mensaje acordado",correction,UUID.randomUUID(),now.plusSeconds(200));
        assertThrows(CollaborationFailure.class,()->d.revise(0,content(),UUID.randomUUID(),o.deadline(),correction));
        assertSame(old,d.submission());
        d.revise(0,content(),UUID.randomUUID(),o.deadline(),correction.minusSeconds(1));
        assertEquals(2,d.submission().revision());assertTrue(d.submission().late());assertNotEquals(old.id(),d.submission().id());
        assertEquals(Deliverable.Status.PENDING_VALIDATION,d.status());
        assertThrows(CollaborationFailure.class,()->d.review(old.id(),0,Deliverable.Decision.APPROVED,null,null,UUID.randomUUID(),correction));
    }
    @Test void rejectionNeedsObservationsAndFutureDeadlineAndCannotReplaceAnApproval() {
        var c=collaboration();var o=c.obligations().getFirst();var d=Deliverable.submit(c.id(),o.id(),1,content(),UUID.randomUUID(),o.deadline(),now);
        assertThrows(IllegalArgumentException.class,()->d.review(d.submission().id(),0,Deliverable.Decision.REJECTED," ",now.plusSeconds(100),UUID.randomUUID(),now));
        assertThrows(IllegalArgumentException.class,()->d.review(d.submission().id(),0,Deliverable.Decision.REJECTED,"Falta evidencia",null,UUID.randomUUID(),now));
        assertThrows(IllegalArgumentException.class,()->d.review(d.submission().id(),0,Deliverable.Decision.REJECTED,"Falta evidencia",now,UUID.randomUUID(),now));
        d.review(d.submission().id(),0,Deliverable.Decision.APPROVED,null,null,UUID.randomUUID(),now);
        assertThrows(CollaborationFailure.class,()->d.revise(0,content(),UUID.randomUUID(),o.deadline(),now));
        assertThrows(CollaborationFailure.class,()->d.review(d.submission().id(),0,Deliverable.Decision.REJECTED,"Cambio",now.plusSeconds(5),UUID.randomUUID(),now));
    }
    @Test void progressRejectsAnotherCollaborationsPiecesAndDuplicateSlots() {
        var c=collaboration();var o=c.obligations().getFirst();
        var foreign=Deliverable.submit(UUID.randomUUID(),o.id(),1,content(),UUID.randomUUID(),o.deadline(),now);
        assertThrows(IllegalArgumentException.class,()->c.reconcile(List.of(foreign),now));
        var one=Deliverable.submit(c.id(),o.id(),1,content(),UUID.randomUUID(),o.deadline(),now);
        var two=Deliverable.submit(c.id(),o.id(),1,content(),UUID.randomUUID(),o.deadline(),now);
        assertThrows(IllegalArgumentException.class,()->c.reconcile(List.of(one,two),now));
    }
    @ParameterizedTest @ValueSource(strings={"javascript:alert(1)","file:///secret","https://user:password@example.com/a","http://example.com/a","https:///missing-host"})
    void refusesInvalidOrUnsafeReferences(String url) {
        assertThrows(IllegalArgumentException.class,()->new DeliveryContent.Reference(url,null));
    }
    @Test void contentNeedsActualEvidenceAndExactlyOneKindOfReference() {
        assertThrows(IllegalArgumentException.class,()->new DeliveryContent.Reference(null,null));
        assertThrows(IllegalArgumentException.class,()->new DeliveryContent.Reference("https://example.com",UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->new DeliveryContent(content().content(),List.of(),null));
        assertThrows(IllegalArgumentException.class,()->new DeliveryContent(content().content(),List.of(content().content(),content().content()),null));
    }
    @Test void uploadedFilesAreInspectedAndCappedBeforeStorage() {
        var files=new DeliveryFileInspector(20);
        var pdf="%PDF-1.7 demo".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals("application/pdf",files.inspect("prueba.pdf","application/pdf",pdf).mediaType());
        assertThrows(CollaborationFailure.class,()->files.inspect("prueba.png","image/png",pdf));
        assertThrows(IllegalArgumentException.class,()->files.inspect("../prueba.pdf","application/pdf",pdf));
        assertThrows(IllegalArgumentException.class,()->files.inspect("prueba.pdf","application/pdf",new byte[0]));
        assertThrows(CollaborationFailure.class,()->files.inspect("prueba.pdf","application/pdf",new byte[21]));
        assertThrows(CollaborationFailure.class,()->files.inspect("prueba.html","text/html","<script>x</script>".getBytes()));
    }
}
