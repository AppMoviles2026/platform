package com.collabtech.platform.identity.application.ports;

import com.collabtech.platform.identity.application.projections.PresentationAudience;
import com.collabtech.platform.identity.application.projections.PublicPresentationView;

/** Provides published presentation copy without coupling the use case to a storage technology. */
public interface PresentationContentProvider {
    PublicPresentationView findByAudience(PresentationAudience audience);
}
