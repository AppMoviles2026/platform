package com.collabtech.platform.performance.infrastructure.configuration;
import com.collabtech.platform.performance.application.ports.AttributionPorts.*;
import com.collabtech.platform.performance.application.ports.PerformancePorts.Access;
import com.collabtech.platform.performance.application.ports.PerformancePorts.UnitOfWork;
import com.collabtech.platform.performance.application.ports.PerformancePorts.Commands;
import com.collabtech.platform.performance.application.services.AttributionService;
import com.collabtech.platform.performance.domain.repositories.PerformanceRepository;
import com.collabtech.platform.performance.infrastructure.persistence.AttributionPersistence;
import com.collabtech.platform.performance.infrastructure.attribution.*;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false) @Profile("!skeleton")
public class AttributionConfiguration {
    @Bean Destinations attributionDestinations(@Value("${performance.attribution.destinations:}")String configured,
            @Value("${performance.attribution.public-base-url:http://localhost:8081}")String base){return new ConfiguredAttributionDestinations(configured,base);}
    @Bean Identifiers attributionIdentifiers(){return new RandomAttributionIdentifiers();}
    @Bean AttributionPersistence attributionPersistence(JdbcTemplate jdbc){return new AttributionPersistence(jdbc);}
    @Bean AttributionService attributionService(Access access,UnitOfWork work,PerformanceRepository reports,AttributionPersistence data,
            Commands commands,Destinations destinations,Identifiers identifiers,Clock clock){
        return new AttributionService(access,work,reports,data,data,commands,destinations,identifiers,clock);
    }
}
