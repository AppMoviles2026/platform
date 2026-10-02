package com.collabtech.platform.shared.application.cqrs;

import com.collabtech.platform.shared.application.cqrs.Query;

public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
}
