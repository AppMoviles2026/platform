package com.collabtech.platform.collaboration.application.ports;
import com.collabtech.platform.collaboration.domain.model.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public final class CollaborationPorts {
    private CollaborationPorts(){}
    public record Actor(UUID accountId,UUID profileId,Agreement.Party party){}
    public interface Participants {
        Actor actor(UUID accountId);
        boolean active(UUID profileId,Agreement.Party party);
    }
    public interface SelectedTerms {AgreementSnapshot capture(UUID actor,UUID application,Instant now);}
    public interface UnitOfWork {<T>T execute(Supplier<T> operation);}
    public interface Reads {
        PageResult<Agreement> agreements(Actor actor,Agreement.Status status,PageRequest page,Instant now);
        PageResult<Collaboration> collaborations(Actor actor,Collaboration.Status status,PageRequest page,Instant now);
        List<UUID> dueAgreements(Instant now,int limit);
        List<UUID> dueCollaborations(Instant now,int limit);
    }
    public interface Events {void append(UUID aggregateId,String type,AgreementSnapshot terms,Instant now);}
}
