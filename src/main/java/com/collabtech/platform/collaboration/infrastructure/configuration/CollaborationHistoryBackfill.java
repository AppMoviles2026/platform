package com.collabtech.platform.collaboration.infrastructure.configuration;

import com.collabtech.platform.collaboration.application.ports.CollaborationPorts.UnitOfWork;
import com.collabtech.platform.collaboration.application.ports.HistoryPorts.History;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("!skeleton")
@Order(5)
public class CollaborationHistoryBackfill implements ApplicationRunner {
    private final History history;
    private final UnitOfWork work;
    public CollaborationHistoryBackfill(History history,UnitOfWork work){this.history=history;this.work=work;}
    public void run(ApplicationArguments args) {
        while(true) {
            var batch=work.execute(()->history.uninitialized(100));
            if(batch.isEmpty())return;
            for(var id:batch)work.execute(()->{history.initialize(id);return null;});
        }
    }
}
