package com.collabtech.platform.performance.interfaces.acl;
import java.util.*;
/** Trusted internal reference lookup after the consumer authorizes its collaboration. */
public interface PerformanceReportFacade {
    record Reference(UUID reportId,UUID collaborationId){}
    Optional<Reference> find(UUID collaborationId);
}
