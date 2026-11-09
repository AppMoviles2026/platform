package com.collabtech.platform.collaboration.infrastructure.configuration;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.application.services.DeliveryService;
import com.collabtech.platform.collaboration.domain.repositories.CollaborationRepository;
import com.collabtech.platform.collaboration.infrastructure.events.CollaborationOutbox;
import com.collabtech.platform.collaboration.infrastructure.files.DeliveryFileInspector;
import com.collabtech.platform.collaboration.infrastructure.persistence.DeliveryPersistence;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
@Profile("!skeleton")
public class DeliveryConfiguration {
    @Bean DeliveryPersistence deliveryPersistence(JdbcTemplate jdbc, ObjectMapper json) { return new DeliveryPersistence(jdbc,json); }
    @Bean DeliveryFileInspector deliveryFileInspector(@Value("${collaboration.files.max-bytes:10485760}") long maxBytes) {
        return new DeliveryFileInspector(maxBytes);
    }
    @Bean DeliveryService deliveryService(Participants participants, UnitOfWork work, CollaborationRepository collaborations,
                                         DeliveryPersistence data, DeliveryFileInspector files, CollaborationOutbox events, Clock clock) {
        return new DeliveryService(participants,work,collaborations,data,data,data,files,data,events,clock);
    }
}
