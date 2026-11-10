package com.collabtech.platform.collaboration.interfaces.acl;
import java.util.*;
/** Internal publication facts, without exposing delivery aggregates, credentials, or private files. */
public interface CollaborationContentFacade {
    record Content(UUID submissionId,UUID deliverableId,String url,boolean current,String reviewStatus){}
    Optional<Content> find(UUID collaborationId,UUID submissionId);
    Set<UUID> currentSubmissions(UUID collaborationId);
}
