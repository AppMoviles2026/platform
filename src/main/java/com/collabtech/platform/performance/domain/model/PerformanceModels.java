package com.collabtech.platform.performance.domain.model;

import java.net.URI;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class PerformanceModels {
    private PerformanceModels(){}
    public enum MetricType { VIEWS,LIKES,COMMENTS,SHARES,REACH,IMPRESSIONS,ENGAGEMENT,CLICKS,CONVERSIONS }
    public enum FetchStatus { AVAILABLE,NO_DATA,AUTHORIZATION_REQUIRED,INSUFFICIENT_SCOPE,PROVIDER_NOT_CONFIGURED,
        UNSUPPORTED_PROVIDER,RATE_LIMITED,PROVIDER_UNAVAILABLE,INVALID_RESPONSE }
    public record Period(Instant start,Instant end) {
        public Period {Objects.requireNonNull(start);Objects.requireNonNull(end);start=precision(start);end=precision(end);
            if(start.isAfter(end))throw new IllegalArgumentException("Invalid period");}
    }
    public record Reference(String url,UUID assetId) {
        public Reference {
            if((url==null)==(assetId==null))throw new IllegalArgumentException("Use exactly one URL or asset ID");
            if(url!=null){url=url.strip();var uri=URI.create(url);
                if(url.length()>2048||!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)
                    throw new IllegalArgumentException("Use a complete HTTPS URL without credentials");}
        }
    }
    public record Publication(UUID id,UUID reportId,UUID socialId,String platform,String postId,Instant createdAt){}
    public record Binding(UUID publicationId,UUID submissionId,UUID deliverableId,String publicationUrl,Instant boundAt){}
    public record Snapshot(UUID id,UUID publicationId,UUID submissionId,Instant capturedAt,Period period,Map<MetricType,Long> metrics,long recordedSequence) {
        public Snapshot {capturedAt=precision(capturedAt);Objects.requireNonNull(period);
            metrics=counts(metrics);if(metrics.isEmpty()||period.end().isAfter(capturedAt))throw new IllegalArgumentException("Invalid automatic observation");}
    }
    public record Refresh(UUID id,UUID publicationId,UUID requestedBy,Instant checkedAt,FetchStatus status,UUID snapshotId,long recordedSequence){}
    public record FetchResult(FetchStatus status,String platform,Period period,Map<MetricType,Long> metrics) {
        public FetchResult {
            Objects.requireNonNull(status);Objects.requireNonNull(platform);metrics=counts(metrics);
            if(status==FetchStatus.AVAILABLE&&(period==null||metrics.isEmpty()))throw new IllegalArgumentException("Available data needs a period and values");
            if(status!=FetchStatus.AVAILABLE&&(!metrics.isEmpty()||period!=null))throw new IllegalArgumentException("Unavailable data has no values");
        }
        public static FetchResult unavailable(FetchStatus status,String platform){return new FetchResult(status,platform,null,Map.of());}
    }
    public static Map<MetricType,Long> counts(Map<MetricType,Long> values) {
        if(values==null)return Map.of();
        var result=new EnumMap<MetricType,Long>(MetricType.class);
        values.forEach((key,value)->{if(key==null||value==null||value<0)throw new IllegalArgumentException("Metric counts must be nonnegative integers");result.put(key,value);});
        return Collections.unmodifiableMap(result);
    }
    public static Instant precision(Instant value){return Objects.requireNonNull(value).truncatedTo(ChronoUnit.MICROS);}
}
