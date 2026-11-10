package com.collabtech.platform.collaboration;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.application.ports.IncidentPorts;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.infrastructure.persistence.*;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationExecutionFacade;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

abstract class IncidentApiFixture extends DeliveryApiFixture {
    @Autowired IncidentService incidentService;
    @Autowired IncidentPersistence incidentData;
    @Autowired HistoryPersistence history;
    @Autowired CollaborationHistoryService historyService;
    @Autowired CollaborationExecutionFacade executionFacts;
    IncidentService incidentAt(Instant instant,IncidentPorts.Events sink) {
        return new IncidentService(participants,work,collaborations,data,incidentData,incidentData,incidentData,sink,data,data,files,
                new IncidentPolicy(),Clock.fixed(instant,ZoneOffset.UTC));
    }
    Map<String,Object> openBody(UUID request,String text) {
        return Map.of("requestId",request,"category","CONDITIONS","description",text,"evidence",List.of());
    }
    JsonNode incident(Result result) {
        assertEquals(201,result.status(),result.body().toString());
        return result.body().path("incident");
    }
    JsonNode openIncident(Execution e,User actor,String text)throws Exception {
        return incident(call("POST","/collaborations/"+e.collaboration()+"/incidents",actor.token(),openBody(UUID.randomUUID(),text)));
    }
    Map<String,Object> keepTerms(String text) {return Map.of("outcome","KEEP_AGREEMENT","description",text,"evidence",List.of());}
    JsonNode propose(Execution e,JsonNode i,User actor,Map<String,Object> terms)throws Exception {
        return incident(call("POST","/incidents/"+i.path("id").asText()+"/resolutions",actor.token(),
                Map.of("requestId",UUID.randomUUID(),"expectedVersion",i.path("version").asLong(),"terms",terms)));
    }
    Result decide(JsonNode i,User actor,String decision,UUID request)throws Exception {
        var body=new HashMap<String,Object>();
        body.put("requestId",request);body.put("expectedVersion",i.path("version").asLong());body.put("decision",decision);
        if(decision.equals("REJECT"))body.put("reason","Debemos aclarar otro punto.");
        return call("POST","/incidents/"+i.path("id").asText()+"/resolutions/"+i.path("currentResolution").path("id").asText()+"/decisions",actor.token(),body);
    }
    JsonNode accept(JsonNode i,User actor)throws Exception {
        var result=decide(i,actor,"ACCEPT",UUID.randomUUID());assertEquals(200,result.status(),result.body().toString());
        return result.body().path("incident");
    }
    JsonNode resolveKeep(Execution e,JsonNode i,User proposer,User accepter)throws Exception {
        return accept(propose(e,i,proposer,keepTerms("Se mantienen las condiciones originales.")),accepter);
    }
    Incident.Terms keepDomain() {return new Incident.Terms(Incident.Outcome.KEEP_AGREEMENT,"Se mantienen las condiciones",List.of(),null,null,null,null);}
}
