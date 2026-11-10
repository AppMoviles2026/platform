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
    RelatedReferences unavailableHistoryReferences() {
        return id->new References(new Reference(id,"NOT_AVAILABLE",null),new Reference(id,"NOT_AVAILABLE",null));
    }
    @Bean CollaborationHistoryService collaborationHistoryService(CollaborationService service,UnitOfWork work,
                                                                   HistoryPersistence history,RelatedReferences references) {
        return new CollaborationHistoryService(service,work,history,references);
    }
}
