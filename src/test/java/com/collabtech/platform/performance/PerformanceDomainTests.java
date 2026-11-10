package com.collabtech.platform.performance;

import static org.junit.jupiter.api.Assertions.*;
import com.collabtech.platform.performance.domain.model.*;
import com.collabtech.platform.performance.domain.model.PerformanceModels.*;
import com.collabtech.platform.performance.domain.exceptions.PerformanceFailure;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PerformanceDomainTests {
    final Instant now=Instant.parse("2026-01-01T12:00:00Z");
    Period period(){return new Period(now.minusSeconds(3600),now);}
    PerformanceEvidence evidence(){return PerformanceEvidence.create(UUID.randomUUID(),UUID.randomUUID(),null,"Captura declarada",
            List.of(new Reference("https://example.com/insights",null)),Map.of(MetricType.VIEWS,25L),now,period(),now,false);}
    @Test void unavailableIsDifferentFromZeroAndMapsAreImmutable() {
        var empty=FetchResult.unavailable(FetchStatus.NO_DATA,"tiktok");
        assertNull(empty.period());assertTrue(empty.metrics().isEmpty());
        var zero=new FetchResult(FetchStatus.AVAILABLE,"tiktok",period(),Map.of(MetricType.VIEWS,0L));
        assertEquals(0L,zero.metrics().get(MetricType.VIEWS));assertFalse(zero.metrics().containsKey(MetricType.REACH));
        assertThrows(UnsupportedOperationException.class,()->zero.metrics().put(MetricType.REACH,1L));
        assertThrows(IllegalArgumentException.class,()->new FetchResult(FetchStatus.NO_DATA,"tiktok",period(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->new FetchResult(FetchStatus.AVAILABLE,"tiktok",period(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->PerformanceModels.counts(Map.of(MetricType.LIKES,-1L)));
    }
    @Test void periodsAndCaptureTimesMustBeConsistent() {
        assertThrows(IllegalArgumentException.class,()->new Period(now,now.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->new Snapshot(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                now.minusSeconds(1),period(),Map.of(MetricType.VIEWS,1L),1));
        assertThrows(IllegalArgumentException.class,()->PerformanceEvidence.create(UUID.randomUUID(),UUID.randomUUID(),null,"Captura",
                evidence().references(),Map.of(),now.minusSeconds(1),period(),now,false));
    }
    @Test void manualReviewPreservesDeclaredValuesAndRequiresNotesOnRejection() {
        var e=evidence();
        assertEquals(PerformanceEvidence.Status.UNVERIFIED,e.status());
        assertFalse(e.demonstration());
        assertThrows(IllegalArgumentException.class,()->e.review(0,PerformanceEvidence.Status.REJECTED,UUID.randomUUID()," ",now));
        e.review(0,PerformanceEvidence.Status.VERIFIED,UUID.randomUUID(),"Revisada",now);
        assertEquals(PerformanceEvidence.Status.VERIFIED,e.status());
        assertEquals(25L,e.claimedMetrics().get(MetricType.VIEWS));
        assertEquals(now,e.observedAt());assertEquals(period(),e.period());
        assertThrows(PerformanceFailure.class,()->e.review(0,PerformanceEvidence.Status.REJECTED,UUID.randomUUID(),"Otra",now));
        assertThrows(PerformanceFailure.class,()->evidence().review(1,PerformanceEvidence.Status.VERIFIED,UUID.randomUUID(),null,now));
    }
    @Test void referencesMustBeDistinctHttpsOrExactlyOneAsset() {
        for(String invalid:List.of("http://example.com/a","https://user:secret@example.com/a","file:///tmp/a","/relative"))
            assertThrows(IllegalArgumentException.class,()->new Reference(invalid,null));
        assertThrows(IllegalArgumentException.class,()->new Reference(null,null));
        assertThrows(IllegalArgumentException.class,()->new Reference("https://example.com/a",UUID.randomUUID()));
        var ref=new Reference("https://example.com/a",null);
        assertThrows(IllegalArgumentException.class,()->PerformanceEvidence.create(UUID.randomUUID(),UUID.randomUUID(),null,"Captura",
                List.of(ref,ref),Map.of(),now,period(),now,false));
        assertThrows(IllegalArgumentException.class,()->PerformanceEvidence.create(UUID.randomUUID(),UUID.randomUUID(),null,"Captura",
                null,Map.of(),now,period(),now,false));
    }
}
