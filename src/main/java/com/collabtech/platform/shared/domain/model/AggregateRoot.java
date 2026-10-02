package com.collabtech.platform.shared.domain.model;

import com.collabtech.platform.shared.domain.events.DomainEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AggregateRoot<ID> extends Entity<ID> {
    private final List<DomainEvent> events = new ArrayList<>();

    protected AggregateRoot(ID id) { super(id); }

    protected final void recordEvent(DomainEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
    }

    public final List<DomainEvent> pullDomainEvents() {
        var pending = List.copyOf(events);
        events.clear();
        return pending;
    }
}
