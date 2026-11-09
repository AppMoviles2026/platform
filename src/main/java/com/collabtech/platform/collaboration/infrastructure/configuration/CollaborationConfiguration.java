package com.collabtech.platform.collaboration.infrastructure.configuration;
import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.*;
import com.collabtech.platform.collaboration.application.services.CollaborationService;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.collaboration.domain.repositories.*;
import com.collabtech.platform.collaboration.infrastructure.persistence.CollaborationPersistence;
import com.collabtech.platform.collaboration.infrastructure.events.CollaborationOutbox;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationEventConsumer;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false) @Profile("!skeleton")
public class CollaborationConfiguration {
    @Bean UnitOfWork collaborationUnitOfWork(PlatformTransactionManager manager){var tx=new TransactionTemplate(manager);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);return new UnitOfWork(){public <T>T execute(Supplier<T> work){return tx.execute(s->work.get());}};}
    @Bean CollaborationPersistence collaborationPersistence(EntityManager em,ObjectMapper json){return new CollaborationPersistence(em,json);}
    @Bean AgreementRepository agreementRepository(CollaborationPersistence db){return new AgreementRepository(){
        public Optional<Agreement> byId(UUID id,boolean lock){return db.agreement(id,lock);}public Optional<Agreement> byApplication(UUID id,boolean lock){return db.agreementForApplication(id,lock);}public Agreement save(Agreement a){return db.save(a);}};}
    @Bean CollaborationRepository collaborationRepository(CollaborationPersistence db){return new CollaborationRepository(){
        public Optional<Collaboration> byId(UUID id,boolean lock){return db.collaboration(id,lock);}public Optional<Collaboration> byAgreement(UUID id){return db.collaborationForAgreement(id);}public Collaboration save(Collaboration c){return db.save(c);}};}
    @Bean CollaborationOutbox collaborationOutbox(JdbcTemplate jdbc,ObjectMapper json,Clock clock,PlatformTransactionManager manager){return new CollaborationOutbox(jdbc,json,clock,manager);}
    @Bean CollaborationService collaborationService(Participants participants,SelectedTerms source,UnitOfWork work,AgreementRepository agreements,CollaborationRepository collaborations,CollaborationPersistence reads,CollaborationOutbox events,Clock clock){
        return new CollaborationService(participants,source,work,agreements,collaborations,reads,events,clock);}
    @Bean CollaborationJobs collaborationJobs(CollaborationService service,CollaborationOutbox events,List<CollaborationEventConsumer> consumers,
            @Value("${collaboration.scheduling.enabled:true}") boolean enabled){return new CollaborationJobs(service,events,consumers,enabled);}
    public static class CollaborationJobs {
        private final CollaborationService service;private final CollaborationOutbox events;private final List<CollaborationEventConsumer> consumers;private final boolean enabled;
        CollaborationJobs(CollaborationService service,CollaborationOutbox events,List<CollaborationEventConsumer> consumers,boolean enabled){this.service=service;this.events=events;this.consumers=List.copyOf(consumers);this.enabled=enabled;}
        @Scheduled(fixedDelayString="${collaboration.expiration.delay-ms:30000}") public void expire(){if(enabled)service.expireDue();}
        @Scheduled(fixedDelayString="${collaboration.events.delay-ms:5000}") public void publish(){if(enabled)events.dispatch(consumers);}
    }
}
