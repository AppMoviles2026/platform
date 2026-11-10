package com.collabtech.platform.performance.domain.repositories;
import com.collabtech.platform.performance.domain.model.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import java.time.Instant;
import java.util.*;
public interface PerformanceRepository {
    Optional<PerformanceReport> report(UUID collaborationId,boolean lock);
    Optional<PerformanceReport> reportById(UUID reportId,boolean lock);
    void create(PerformanceReport report);
    void touch(UUID reportId,Instant now);
    Optional<Publication> publication(UUID report,UUID social,String post);
    void save(Publication publication,Binding binding);
    Optional<Publication> publicationById(UUID id);
    List<Binding> bindings(UUID publication);
    void save(Snapshot snapshot);
    void save(Refresh refresh);
    Optional<Refresh> refresh(UUID id);
    Optional<Snapshot> snapshot(UUID id);
    Optional<Refresh> latestRefresh(UUID publication);
    Optional<Snapshot> latestSnapshot(UUID publication);
    Optional<PerformanceEvidence> evidence(UUID id);
    void save(PerformanceEvidence evidence);
}
