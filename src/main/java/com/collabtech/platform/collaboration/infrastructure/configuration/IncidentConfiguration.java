package com.collabtech.platform.collaboration.infrastructure.configuration;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.application.ports.IncidentPorts.*;
import com.collabtech.platform.collaboration.application.services.*;
import com.collabtech.platform.collaboration.domain.model.IncidentPolicy;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.collaboration.infrastructure.events.CollaborationOutbox;
import com.collabtech.platform.collaboration.infrastructure.files.DeliveryFileInspector;
import com.collabtech.platform.collaboration.infrastructure.persistence.*;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
@Profile("!skeleton")
public class IncidentConfiguration {
    @Bean IncidentPolicy incidentPolicy(){return new IncidentPolicy();}
    @Bean IncidentPersistence incidentPersistence(JdbcTemplate jdbc,ObjectMapper json){return new IncidentPersistence(jdbc,json);}
    @Bean HistoryPersistence historyPersistence(JdbcTemplate jdbc,ObjectMapper json){return new HistoryPersistence(jdbc,json);}
    @Bean IncidentService incidentService(Participants participants,UnitOfWork work,CollaborationRepository collaborations,
                                          DeliverableRepository deliveries,IncidentPersistence incidents,CollaborationOutbox events,
                                          DeliveryPersistence data,DeliveryFileInspector files,IncidentPolicy policy,Clock clock) {
        return new IncidentService(participants,work,collaborations,deliveries,incidents,incidents,incidents,events,data,data,files,policy,clock);
    }
    @Bean @ConditionalOnMissingBean(RelatedReferences.class)
    RelatedReferences historyRelatedReferences(org.springframework.beans.factory.ObjectProvider<com.collabtech.platform.performance.interfaces.acl.PerformanceReportFacade> performance,
        org.springframework.beans.factory.ObjectProvider<com.collabtech.platform.billing.interfaces.acl.CompensationReferenceFacade> billing) {
        return id->{
            var facade=performance.getIfAvailable();
            var reference=facade==null?java.util.Optional.<com.collabtech.platform.performance.interfaces.acl.PerformanceReportFacade.Reference>empty():facade.find(id);
            var compensation=billing.getIfAvailable();var cash=compensation==null?java.util.Optional.<java.util.UUID>empty():compensation.find(id);
            return new References(new Reference(id,cash.isPresent()?"AVAILABLE":"NOT_AVAILABLE",cash.map(java.util.UUID::toString).orElse(null)),new Reference(id,reference.isPresent()?"AVAILABLE":"NOT_AVAILABLE",reference.map(r->r.reportId().toString()).orElse(null)));
        };
    }
    @Bean CollaborationHistoryService collaborationHistoryService(CollaborationService service,UnitOfWork work,
                                                                   HistoryPersistence history,RelatedReferences references) {
        return new CollaborationHistoryService(service,work,history,references);
    }
}
