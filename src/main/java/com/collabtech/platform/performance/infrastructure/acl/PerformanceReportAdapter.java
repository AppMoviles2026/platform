package com.collabtech.platform.performance.infrastructure.acl;
import com.collabtech.platform.performance.interfaces.acl.PerformanceReportFacade;
import com.collabtech.platform.performance.domain.repositories.PerformanceRepository;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @Profile("!skeleton")
public class PerformanceReportAdapter implements PerformanceReportFacade {
    private final PerformanceRepository repository;
    public PerformanceReportAdapter(PerformanceRepository repository){this.repository=repository;}
    @Transactional(readOnly=true)
    public Optional<Reference> find(UUID collaboration){return repository.report(collaboration,false).map(r->new Reference(r.id(),collaboration));}
}
