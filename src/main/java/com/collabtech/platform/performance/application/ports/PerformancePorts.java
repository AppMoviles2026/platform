package com.collabtech.platform.performance.application.ports;
import com.collabtech.platform.performance.domain.model.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.shared.application.pagination.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public final class PerformancePorts {
    private PerformancePorts(){}
    public record Actor(UUID accountId,UUID profileId,String role){}
    public record Scope(UUID collaborationId,UUID brandId,UUID creatorId,boolean cancelled){}
    public record Content(UUID submissionId,UUID deliverableId,String url,boolean current,String reviewStatus){}
    public interface Access {
        Actor actor(UUID account);
        Scope scope(UUID collaboration,boolean lock);
        Content content(UUID collaboration,UUID submission);
        Set<UUID> currentSubmissions(UUID collaboration);
        String socialPlatform(UUID creator,UUID social);
    }
    public interface UnitOfWork {<T>T execute(Supplier<T> action);}
    public interface SocialMetricsProvider {FetchResult fetch(UUID creator,UUID social,String platform,String postId);}
    public record Asset(UUID id,UUID reportId,UUID owner,String filename,String mediaType,long byteSize,String sha256,Instant createdAt){}
    public record FileInfo(String filename,String mediaType,long byteSize,String sha256){}
    public interface Files {FileInfo inspect(String name,String media,byte[] bytes);void save(Asset asset,byte[] bytes);
        Optional<Asset> asset(UUID id);byte[] bytes(UUID id);boolean shared(UUID id);}
    public record Command(String operation,String fingerprint,UUID resultId){}
    public interface Commands {Optional<Command> command(UUID report,UUID actor,UUID request);
        void command(UUID report,UUID actor,UUID request,Command result,Instant now);String fingerprint(Object input);}
    public interface Queries {
        PageResult<Publication> publications(UUID report,PageRequest page);
        PageResult<Snapshot> snapshots(UUID report,PageRequest page);
        PageResult<PerformanceEvidence> evidence(UUID report,PerformanceEvidence.Status status,PageRequest page);
        long evidenceCount(UUID report);
        long availablePublications(UUID report,Set<UUID> currentSubmissions);
    }
}
