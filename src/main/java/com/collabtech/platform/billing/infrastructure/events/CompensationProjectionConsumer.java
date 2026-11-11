package com.collabtech.platform.billing.infrastructure.events;
import com.collabtech.platform.collaboration.interfaces.acl.CollaborationEventConsumer;
import com.collabtech.platform.billing.application.ports.CompensationPorts.Context;
import com.collabtech.platform.billing.application.services.CompensationService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component @Profile("!skeleton")
public class CompensationProjectionConsumer implements CollaborationEventConsumer {
    private final CompensationService service;private final Context context;
    public CompensationProjectionConsumer(CompensationService service,Context context){this.service=service;this.context=context;}
    public String consumerName(){return "billing.compensation";}
    public void handle(Event event){if(context.execution(event.collaborationId(),false).isPresent())service.project(event.collaborationId());}
}
