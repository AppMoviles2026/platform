package com.collabtech.platform.performance.domain.model;

import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure.Code;
import java.time.Instant;
import java.util.*;

public final class PerformanceEvidence {
    public enum Status { UNVERIFIED,VERIFIED,REJECTED }
    private final UUID id,reportId,submittedBy,submissionId;
    private final String description;
    private final List<Reference> references;
    private final Map<MetricType,Long> claimedMetrics;
    private final Instant observedAt,submittedAt;
    private final Period period;
    private final boolean demonstration;
    private Status status;
    private long version;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String observations;
    public PerformanceEvidence(UUID id,UUID report,UUID by,UUID submission,String description,List<Reference> references,
            Map<MetricType,Long> claims,Instant observed,Period period,Instant submitted,boolean demo,Status status,long version,
            UUID reviewedBy,Instant reviewedAt,String observations) {
        this.id=Objects.requireNonNull(id);this.reportId=Objects.requireNonNull(report);this.submittedBy=Objects.requireNonNull(by);
        this.submissionId=submission;
        if(description==null||description.isBlank()||description.strip().length()>4000)throw new IllegalArgumentException("Description is required");
        if(references==null||references.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Evidence references are required");
        this.description=description.strip();this.references=List.copyOf(references);
        if(this.references.isEmpty()||this.references.size()>10||this.references.stream().distinct().count()!=this.references.size())throw new IllegalArgumentException("Use one to ten distinct references");
        this.claimedMetrics=PerformanceModels.counts(claims);this.observedAt=PerformanceModels.precision(observed);
        this.period=Objects.requireNonNull(period);this.submittedAt=PerformanceModels.precision(submitted);
        if(period.end().isAfter(this.observedAt)||this.observedAt.isAfter(this.submittedAt))throw new IllegalArgumentException("Evidence cannot claim a future observation or period");
        this.demonstration=demo;this.status=Objects.requireNonNull(status);this.version=version;
        this.reviewedBy=reviewedBy;this.reviewedAt=reviewedAt;this.observations=observations;
        if(version<0)throw new IllegalArgumentException("Invalid version");
    }
    public static PerformanceEvidence create(UUID report,UUID by,UUID submission,String description,List<Reference> refs,
            Map<MetricType,Long> metrics,Instant observed,Period period,Instant now,boolean demonstration) {
        return new PerformanceEvidence(UUID.randomUUID(),report,by,submission,description,refs,metrics,observed,period,now,
                demonstration,Status.UNVERIFIED,0,null,null,null);
    }
    public void review(long expected,Status decision,UUID actor,String notes,Instant now) {
        if(version!=expected)throw new PerformanceFailure(Code.CONCURRENT_UPDATE);
        if(status!=Status.UNVERIFIED)throw new PerformanceFailure(Code.EVIDENCE_ALREADY_REVIEWED);
        if(decision==null||decision==Status.UNVERIFIED)throw new IllegalArgumentException("Select VERIFIED or REJECTED");
        notes=notes==null?null:notes.strip();
        if(notes!=null&&notes.length()>2000||decision==Status.REJECTED&&(notes==null||notes.isBlank()))throw new IllegalArgumentException("Rejection requires observations, up to 2000 characters");
        status=decision;reviewedBy=actor;reviewedAt=PerformanceModels.precision(now);observations=notes;
    }
    public UUID id(){return id;}public UUID reportId(){return reportId;}public UUID submittedBy(){return submittedBy;}
    public UUID submissionId(){return submissionId;}public String description(){return description;}public List<Reference> references(){return references;}
    public Map<MetricType,Long> claimedMetrics(){return claimedMetrics;}public Instant observedAt(){return observedAt;}public Period period(){return period;}
    public Instant submittedAt(){return submittedAt;}public boolean demonstration(){return demonstration;}public Status status(){return status;}public long version(){return version;}
    public UUID reviewedBy(){return reviewedBy;}public Instant reviewedAt(){return reviewedAt;}public String observations(){return observations;}
    public void restoreVersion(long value){version=value;}
}
