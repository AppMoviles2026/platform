package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.exceptions.CollaborationFailure;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IncidentApiTests extends IncidentApiFixture {
    @Test void eachIncidentKeepsItsOwnBlockUntilItsCounterpartyAccepts()throws Exception {
        var e=execution(1);var d=piece(submit(e,"before-dispute"));
        var first=openIncident(e,e.fixture().creator(),"Aclarar el mensaje.");
        var second=openIncident(e,e.fixture().brand(),"Aclarar la evidencia.");
        var view=collaboration(e,e.fixture().creator());
        assertEquals("DISPUTED",view.path("status").asText());assertEquals(2,view.path("openIncidentCount").asInt());
        assertEquals(409,approve(e,d).status());assertEquals(409,submit(e,"blocked").status());
        assertTrue(view.path("pendingActions").toString().contains("RESOLVE_INCIDENTS"));
        var proposal=propose(e,first,e.fixture().brand(),keepTerms("Mantener el acuerdo inicial."));
        assertEquals(403,decide(proposal,e.fixture().brand(),"ACCEPT",UUID.randomUUID()).status());
        accept(proposal,e.fixture().creator());
        assertEquals(1,collaboration(e,e.fixture().creator()).path("openIncidentCount").asInt());
        assertEquals("DISPUTED",collaboration(e,e.fixture().brand()).path("status").asText());
        resolveKeep(e,second,e.fixture().creator(),e.fixture().brand());
        assertEquals("UNDER_REVIEW",collaboration(e,e.fixture().brand()).path("status").asText());
        assertEquals(0,collaboration(e,e.fixture().creator()).path("openIncidentCount").asInt());
        assertEquals(2,events(e,"IncidentOpened"));assertEquals(2,events(e,"IncidentResolved"));
        assertEquals(1,events(e,"CollaborationDisputed"));assertEquals(1,events(e,"CollaborationDisputeCleared"));
        assertEquals(200,approve(e,d).status());
    }

    @Test void proposalsAreImmutableAndRejectionWithdrawalAndOldVersionsAreHandled()throws Exception {
        var e=execution(1);var i=openIncident(e,e.fixture().creator(),"Revisar las condiciones");
        var first=propose(e,i,e.fixture().brand(),keepTerms("Primera propuesta"));
        assertEquals(409,call("POST","/incidents/"+i.path("id").asText()+"/resolutions",e.fixture().creator().token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",first.path("version").asLong(),"terms",keepTerms("No debe reemplazarla"))).status());
        var rejected=decide(first,e.fixture().creator(),"REJECT",UUID.randomUUID());
        assertEquals(200,rejected.status());var reopened=rejected.body().path("incident");
        assertEquals("OPEN",reopened.path("status").asText());
        assertEquals("DISPUTED",collaboration(e,e.fixture().brand()).path("status").asText());
        var second=propose(e,reopened,e.fixture().creator(),keepTerms("Segunda propuesta"));
        assertEquals(403,decide(second,e.fixture().brand(),"WITHDRAW",UUID.randomUUID()).status());
        var withdrawn=decide(second,e.fixture().creator(),"WITHDRAW",UUID.randomUUID());
        assertEquals(200,withdrawn.status());assertEquals("OPEN",withdrawn.body().path("incident").path("status").asText());
        assertEquals(409,decide(first,e.fixture().creator(),"ACCEPT",UUID.randomUUID()).status());
        var list=call("GET","/incidents/"+i.path("id").asText()+"/resolutions?size=1",e.fixture().creator().token(),null);
        assertEquals(2,list.body().path("total").asInt());
        assertEquals(1,collaboration(e,e.fixture().brand()).path("openIncidentCount").asInt());
    }

    @Test void aMutuallyAcceptedExtensionReopensOnlyTheRejectedVersionWithoutRewritingItsReview()throws Exception {
        var e=execution(1);var d=piece(submit(e,"needs-correction"));
        Instant originalDeadline=Instant.now().plusSeconds(60);
        var rejected=reject(e,d,originalDeadline);UUID target=UUID.fromString(d.path("id").asText());
        UUID creator=UUID.fromString(e.fixture().creator().account()),brand=UUID.fromString(e.fixture().brand().account());
        var time=originalDeadline.plusSeconds(1);var later=incidentAt(time,events);
        var opened=later.open(creator,UUID.fromString(e.collaboration()),new IncidentService.Open(UUID.randomUUID(),Incident.Category.DELIVERABLE,"Necesitamos un nuevo plazo",target,List.of())).incident();
        var newDeadline=originalDeadline.plusSeconds(600);
        var terms=new Incident.Terms(Incident.Outcome.EXTEND_CORRECTION,"Ampliar el plazo de la pieza rechazada",List.of(),target,
                UUID.fromString(d.path("currentSubmission").path("id").asText()),rejected.path("version").asLong(),newDeadline);
        var proposed=later.propose(brand,opened.id(),new IncidentService.Propose(UUID.randomUUID(),opened.version(),terms)).incident();
        assertThrows(CollaborationFailure.class,()->atDelivery(time,events).correct(creator,target,
                new DeliveryService.Correction(UUID.randomUUID(),rejected.path("version").asLong(),domainContent("blocked"))));
        later.decide(creator,opened.id(),proposed.currentResolution().id(),new IncidentService.Decide(UUID.randomUUID(),proposed.version(),Incident.Decision.ACCEPT,null));
        var current=delivery.get(creator,target);
        assertEquals(Instant.parse(rejected.path("currentReview").path("correctionDeadline").asText()),current.currentReview().correctionDeadline());
        assertEquals(newDeadline.truncatedTo(java.time.temporal.ChronoUnit.MICROS),current.effectiveCorrectionDeadline());
        var history=delivery.revisions(creator,target,new com.collabtech.platform.shared.application.pagination.PageRequest(0,20));
        assertEquals(1,history.items().getFirst().correctionExtensions().size());
        var fixed=atDelivery(newDeadline.minusSeconds(1),events).correct(creator,target,
                new DeliveryService.Correction(UUID.randomUUID(),current.version(),domainContent("fixed")));
        assertEquals(2,fixed.deliverable().currentSubmission().revision());
        assertEquals(1,events(e,"CorrectionWindowExtended"));
        assertEquals(e.deadline(),Instant.parse(collaboration(e,e.fixture().creator()).path("snapshot").path("deliverables").get(0).path("deadline").asText()));
    }

    @Test void acceptedCancellationIsBilateralAndDoesNotClearOtherIncidents()throws Exception {
        var e=execution(1);var first=openIncident(e,e.fixture().creator(),"No continuaremos");
        var second=openIncident(e,e.fixture().brand(),"Dejar constancia del cierre");
        var proposed=propose(e,first,e.fixture().brand(),Map.of("outcome","CANCEL_COLLABORATION","description","Cancelar de común acuerdo","evidence",List.of()));
        var request=UUID.randomUUID();assertEquals(200,decide(proposed,e.fixture().creator(),"ACCEPT",request).status());
        var disputed=collaboration(e,e.fixture().creator());assertEquals("DISPUTED",disputed.path("status").asText());
        assertFalse(disputed.path("cancelledAt").isNull());assertEquals(1,disputed.path("openIncidentCount").asInt());
        assertEquals(200,decide(proposed,e.fixture().creator(),"ACCEPT",request).status());
        resolveKeep(e,second,e.fixture().creator(),e.fixture().brand());
        assertEquals("CANCELLED",collaboration(e,e.fixture().brand()).path("status").asText());
        assertEquals(1,events(e,"CollaborationCancelled"));assertFalse(executionFacts.find(UUID.fromString(e.collaboration())).orElseThrow().compensationEligible());
        assertEquals(409,submit(e,"after-cancel").status());
    }

    @Test void recentCompletedWorkKeepsItsCompletionAndEligibilityIsBlockedOnlyWhileClaimsRemain()throws Exception {
        var e=execution(1);approve(e,piece(submit(e,"completed")));
        var original=collaboration(e,e.fixture().brand());UUID id=UUID.fromString(e.collaboration());
        assertTrue(executionFacts.find(id).orElseThrow().compensationEligible());
        var i=openIncident(e,e.fixture().creator(),"Aclaración posterior a la entrega");
        assertEquals("DISPUTED",collaboration(e,e.fixture().creator()).path("status").asText());
        assertFalse(executionFacts.find(id).orElseThrow().compensationEligible());
        assertEquals(409,call("POST","/incidents/"+i.path("id").asText()+"/resolutions",e.fixture().brand().token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",i.path("version").asLong(),"terms",
                        Map.of("outcome","CANCEL_COLLABORATION","description","No se borra el cumplimiento","evidence",List.of()))).status());
        resolveKeep(e,i,e.fixture().brand(),e.fixture().creator());
        var restored=collaboration(e,e.fixture().creator());assertEquals("COMPLETED",restored.path("status").asText());
        assertEquals(original.path("completedAt"),restored.path("completedAt"));assertTrue(executionFacts.find(id).orElseThrow().compensationEligible());
        assertEquals(0,events(e,"CompensationPaid"));
    }

    @Test void openingWindowEndsAtSevenDaysButExistingIncidentsCanBeResolvedLater()throws Exception {
        var e=execution(1);approve(e,piece(submit(e,"seven-days")));
        Instant completed=Instant.parse(collaboration(e,e.fixture().brand()).path("completedAt").asText());
        UUID creator=UUID.fromString(e.fixture().creator().account()),brand=UUID.fromString(e.fixture().brand().account()),c=UUID.fromString(e.collaboration());
        var before=incidentAt(completed.plus(Duration.ofDays(7)).minusSeconds(1),events);
        var openBody=new IncidentService.Open(UUID.randomUUID(),Incident.Category.OTHER,"Último instante de apertura",null,List.of());
        var opened=before.open(creator,c,openBody);
        var boundary=incidentAt(completed.plus(Duration.ofDays(7)),events);
        var failure=assertThrows(CollaborationFailure.class,()->boundary.open(brand,c,new IncidentService.Open(UUID.randomUUID(),Incident.Category.OTHER,"Fuera de plazo",null,List.of())));
        assertEquals("INCIDENT_WINDOW_CLOSED",failure.code().name());
        var after=incidentAt(completed.plus(Duration.ofDays(9)),events);
        var p=after.propose(brand,opened.incident().id(),new IncidentService.Propose(UUID.randomUUID(),opened.incident().version(),keepDomain())).incident();
        after.decide(creator,p.id(),p.currentResolution().id(),new IncidentService.Decide(UUID.randomUUID(),p.version(),Incident.Decision.ACCEPT,null));
        assertTrue(after.open(creator,c,openBody).replayed());
        assertEquals("COMPLETED",collaboration(e,e.fixture().creator()).path("status").asText());
        assertFalse(new IncidentPolicy().canOpen(work.execute(()->collaborations.byId(c,false).orElseThrow()),completed.plus(Duration.ofDays(7))));
    }

    @Test void idempotencyAndSimultaneousOpeningsKeepExactlyOneIncidentPerRequest()throws Exception {
        var e=execution(1);UUID request=UUID.randomUUID();var body=openBody(request,"Una sola incidencia");var gate=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();return call("POST","/collaborations/"+e.collaboration()+"/incidents",e.fixture().creator().token(),body);});
            var b=pool.submit(()->{gate.await();return call("POST","/collaborations/"+e.collaboration()+"/incidents",e.fixture().creator().token(),body);});
            assertEquals(incident(a.get(30,TimeUnit.SECONDS)).path("id"),incident(b.get(30,TimeUnit.SECONDS)).path("id"));
        }
        assertEquals(1,collaboration(e,e.fixture().creator()).path("openIncidentCount").asInt());
        assertEquals(409,call("POST","/collaborations/"+e.collaboration()+"/incidents",e.fixture().creator().token(),openBody(request,"Otro cuerpo")).status());
        assertEquals(1,events(e,"IncidentOpened"));assertEquals(1,events(e,"CollaborationDisputed"));
    }

    @Test void competingDecisionsAndIndependentResolutionsCannotOverwriteEachOther()throws Exception {
        var e=execution(1);var i=openIncident(e,e.fixture().creator(),"Decisión simultánea");
        var p=propose(e,i,e.fixture().brand(),keepTerms("Mantener"));var gate=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();return decide(p,e.fixture().creator(),"ACCEPT",UUID.randomUUID());});
            var b=pool.submit(()->{gate.await();return decide(p,e.fixture().creator(),"REJECT",UUID.randomUUID());});
            assertEquals(Set.of(200,409),Set.of(a.get(30,TimeUnit.SECONDS).status(),b.get(30,TimeUnit.SECONDS).status()));
        }
        assertEquals(1,events(e,"IncidentResolved")+events(e,"ResolutionRejected"));
        var other=execution(1);var first=propose(other,openIncident(other,other.fixture().creator(),"Primero"),other.fixture().brand(),keepTerms("Uno"));
        var second=propose(other,openIncident(other,other.fixture().brand(),"Segundo"),other.fixture().creator(),keepTerms("Dos"));
        var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return decide(first,other.fixture().creator(),"ACCEPT",UUID.randomUUID());});
            var b=pool.submit(()->{start.await();return decide(second,other.fixture().brand(),"ACCEPT",UUID.randomUUID());});
            assertEquals(200,a.get(30,TimeUnit.SECONDS).status());assertEquals(200,b.get(30,TimeUnit.SECONDS).status());
        }
        assertEquals(0,collaboration(other,other.fixture().creator()).path("openIncidentCount").asInt());
    }

    @Test void staleDeliveryExtensionAndExpiredProposalRemainUnresolved()throws Exception {
        var e=execution(1);var d=piece(submit(e,"extension-race"));var rejected=reject(e,d,Instant.now().plusSeconds(100));
        UUID creator=UUID.fromString(e.fixture().creator().account()),brand=UUID.fromString(e.fixture().brand().account());
        UUID target=UUID.fromString(d.path("id").asText());Instant now=Instant.now();
        var a=openIncident(e,e.fixture().creator(),"Primer plazo");var b=openIncident(e,e.fixture().brand(),"Segundo plazo");
        var terms=new Incident.Terms(Incident.Outcome.EXTEND_CORRECTION,"Ampliar",List.of(),target,UUID.fromString(d.path("currentSubmission").path("id").asText()),rejected.path("version").asLong(),now.plusSeconds(500));
        var one=incidentService.propose(brand,UUID.fromString(a.path("id").asText()),new IncidentService.Propose(UUID.randomUUID(),a.path("version").asLong(),terms)).incident();
        var two=incidentService.propose(creator,UUID.fromString(b.path("id").asText()),new IncidentService.Propose(UUID.randomUUID(),b.path("version").asLong(),terms)).incident();
        incidentService.decide(creator,one.id(),one.currentResolution().id(),new IncidentService.Decide(UUID.randomUUID(),one.version(),Incident.Decision.ACCEPT,null));
        assertThrows(CollaborationFailure.class,()->incidentService.decide(brand,two.id(),two.currentResolution().id(),
                new IncidentService.Decide(UUID.randomUUID(),two.version(),Incident.Decision.ACCEPT,null)));
        assertEquals(1,collaboration(e,e.fixture().creator()).path("openIncidentCount").asInt());
        assertEquals("RESOLUTION_PROPOSED",incidentService.get(creator,two.id()).status().name());
        assertEquals(1,jdbc.queryForObject("select count(*) from collaboration_correction_extension where deliverable_id=?",Integer.class,target.toString()));
    }

    @Test void failedEventAndHistoryWritesRollBackIncidentAndResolutionSideEffects()throws Exception {
        var e=execution(1);UUID c=UUID.fromString(e.collaboration()),creator=UUID.fromString(e.fixture().creator().account()),brand=UUID.fromString(e.fixture().brand().account());
        long before=jdbc.queryForObject("select count(*) from collaboration_history where collaboration_id=?",Long.class,e.collaboration());
        var broken=incidentAt(Instant.now(),event->{events.append(event);throw new IllegalStateException("Controlled fault after audit");});
        var open=new IncidentService.Open(UUID.randomUUID(),Incident.Category.CONDITIONS,"Debe ser atómico",null,List.of());
        assertThrows(IllegalStateException.class,()->broken.open(creator,c,open));
        assertEquals(0,count("collaboration_incident","collaboration_id",e.collaboration()));
        assertEquals(before,jdbc.queryForObject("select count(*) from collaboration_history where collaboration_id=?",Long.class,e.collaboration()));
        assertEquals(0,collaboration(e,e.fixture().creator()).path("openIncidentCount").asInt());
        var i=incidentService.open(creator,c,open).incident();
        var p=incidentService.propose(brand,i.id(),new IncidentService.Propose(UUID.randomUUID(),i.version(),keepDomain())).incident();
        UUID request=UUID.randomUUID();var decision=new IncidentService.Decide(request,p.version(),Incident.Decision.ACCEPT,null);
        assertThrows(IllegalStateException.class,()->broken.decide(creator,i.id(),p.currentResolution().id(),decision));
        assertEquals(1,collaboration(e,e.fixture().creator()).path("openIncidentCount").asInt());
        assertEquals("RESOLUTION_PROPOSED",incidentService.get(creator,i.id()).status().name());
        assertEquals("RESOLVED",incidentService.decide(creator,i.id(),p.currentResolution().id(),decision).incident().status().name());
    }


    @Test void failedExtensionAcceptanceRollsBackTheAmendmentAndOriginalReviewRemainsUnchanged()throws Exception {
        var e=execution(1);var d=piece(submit(e,"atomic-extension"));
        var rejected=reject(e,d,Instant.now().plusSeconds(100));
        UUID target=UUID.fromString(d.path("id").asText()),creator=UUID.fromString(e.fixture().creator().account()),brand=UUID.fromString(e.fixture().brand().account());
        var claim=openIncident(e,e.fixture().creator(),"Solicitar ampliación");
        var terms=new Incident.Terms(Incident.Outcome.EXTEND_CORRECTION,"Ampliar de común acuerdo",List.of(),target,
                UUID.fromString(d.path("currentSubmission").path("id").asText()),rejected.path("version").asLong(),Instant.now().plusSeconds(1000));
        var proposed=incidentService.propose(brand,UUID.fromString(claim.path("id").asText()),
                new IncidentService.Propose(UUID.randomUUID(),claim.path("version").asLong(),terms)).incident();
        var decision=new IncidentService.Decide(UUID.randomUUID(),proposed.version(),Incident.Decision.ACCEPT,null);
        var broken=incidentAt(Instant.now(),event->{events.append(event);throw new IllegalStateException("Rollback extension and audit");});
        assertThrows(IllegalStateException.class,()->broken.decide(creator,proposed.id(),proposed.currentResolution().id(),decision));
        assertEquals(0,jdbc.queryForObject("select count(*) from collaboration_correction_extension where deliverable_id=?",Integer.class,target.toString()));
        assertNull(work.execute(()->data.byId(target).orElseThrow().correctionDeadlineOverride()));
        assertEquals(rejected.path("version").asLong(),delivery.get(creator,target).version());
        assertEquals("RESOLUTION_PROPOSED",incidentService.get(creator,proposed.id()).status().name());
        assertEquals(1,collaboration(e,e.fixture().creator()).path("openIncidentCount").asInt());
        incidentService.decide(creator,proposed.id(),proposed.currentResolution().id(),decision);
        assertEquals(1,jdbc.queryForObject("select count(*) from collaboration_correction_extension where deliverable_id=?",Integer.class,target.toString()));
    }

    @Test void participantAuthorizationAndInputValidationCoverEveryIncidentRoute()throws Exception {
        var e=execution(1);var foreign=user("creators");var i=openIncident(e,e.fixture().creator(),"Privado");
        String id=i.path("id").asText(),root="/collaborations/"+e.collaboration()+"/incidents";
        assertEquals(401,call("GET",root,null,null).status());
        assertEquals(403,call("GET",root,foreign.token(),null).status());
        assertEquals(403,call("GET","/incidents/"+id,foreign.token(),null).status());
        assertEquals(403,call("GET","/incidents/"+id+"/resolutions",foreign.token(),null).status());
        assertEquals(403,call("POST",root,foreign.token(),openBody(UUID.randomUUID(),"Ajeno")).status());
        assertEquals(400,call("GET",root+"?status=UNKNOWN",e.fixture().brand().token(),null).status());
        assertEquals(400,call("GET",root+"?size=101",e.fixture().creator().token(),null).status());
        assertEquals(404,call("GET","/incidents/"+UUID.randomUUID(),e.fixture().brand().token(),null).status());
        var invalid=new HashMap<String,Object>(openBody(UUID.randomUUID()," "));assertEquals(400,call("POST",root,e.fixture().creator().token(),invalid).status());
        invalid=new HashMap<>(openBody(UUID.randomUUID(),"Null evidence"));invalid.put("evidence",Arrays.asList((Object)null));
        assertEquals(400,call("POST",root,e.fixture().creator().token(),invalid).status());
        invalid=new HashMap<>(openBody(UUID.randomUUID(),"Falsificación"));invalid.put("resolvedAt",Instant.now());
        assertEquals(400,call("POST",root,e.fixture().creator().token(),invalid).status());
        assertEquals(1,count("collaboration_incident","collaboration_id",e.collaboration()));
    }

    @Test void incidentFilesArePrivateBeforeSharingAndCanBeSuppliedByEitherParticipant()throws Exception {
        var e=execution(1);UUID brand=UUID.fromString(e.fixture().brand().account()),creator=UUID.fromString(e.fixture().creator().account()),c=UUID.fromString(e.collaboration());
        var bytes="%PDF-1.7 evidence".getBytes(StandardCharsets.US_ASCII);UUID request=UUID.randomUUID();
        var asset=incidentService.upload(brand,c,request,"claim.pdf","application/pdf",bytes);
        assertEquals(asset.asset().id(),incidentService.upload(brand,c,request,"claim.pdf","application/pdf",bytes).asset().id());
        assertEquals(403,call("GET","/collaboration-assets/"+asset.asset().id(),e.fixture().creator().token(),null).status());
        var body=new HashMap<String,Object>(openBody(UUID.randomUUID(),"Evidencia empresarial"));
        body.put("evidence",List.of(Map.of("assetId",asset.asset().id())));
        var opened=incident(call("POST","/collaborations/"+e.collaboration()+"/incidents",e.fixture().brand().token(),body));
        assertArrayEquals(bytes,delivery.download(creator,asset.asset().id()).bytes());
        resolveKeep(e,opened,e.fixture().creator(),e.fixture().brand());
        assertArrayEquals(bytes,delivery.download(creator,asset.asset().id()).bytes());
        var other=execution(1);
        assertEquals(403,call("POST","/collaborations/"+other.collaboration()+"/incidents",other.fixture().creator().token(),body).status());
    }
}
