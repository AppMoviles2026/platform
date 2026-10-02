package com.collabtech.platform.identity.application.ports;

import com.collabtech.platform.identity.domain.model.valueobjects.EmailAddress;

/** Future implementation must persist expiring, single-use hashed tokens and send through a provider. */
public interface AccountRecoveryService {
    void requestRecovery(EmailAddress email);
}
