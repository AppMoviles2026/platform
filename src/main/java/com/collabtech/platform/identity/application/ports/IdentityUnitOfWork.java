package com.collabtech.platform.identity.application.ports;

import java.util.function.Supplier;

public interface IdentityUnitOfWork {
    <T> T execute(Supplier<T> work);
}
