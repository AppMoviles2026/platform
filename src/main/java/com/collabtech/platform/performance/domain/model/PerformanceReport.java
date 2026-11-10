package com.collabtech.platform.performance.domain.model;
import java.time.Instant;
import java.util.*;
public record PerformanceReport(UUID id,UUID collaborationId,UUID brandId,UUID creatorId,Instant createdAt,Instant updatedAt,long version) {
    public PerformanceReport {Objects.requireNonNull(id);Objects.requireNonNull(collaborationId);Objects.requireNonNull(brandId);Objects.requireNonNull(creatorId);
        if(version<0)throw new IllegalArgumentException("Invalid report version");}
    public static PerformanceReport create(UUID collaboration,UUID brand,UUID creator,Instant now) {
        now=PerformanceModels.precision(now);return new PerformanceReport(UUID.randomUUID(),collaboration,brand,creator,now,now,0);
    }
}
