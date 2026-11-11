package com.collabtech.platform.billing.interfaces.acl;
import java.util.*;
/** Trusted participant-authorized history consumers receive no financial credentials. */
public interface CompensationReferenceFacade {Optional<UUID> find(UUID collaborationId);}
