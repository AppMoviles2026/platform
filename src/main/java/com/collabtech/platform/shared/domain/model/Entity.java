package com.collabtech.platform.shared.domain.model;

import java.util.Objects;

public abstract class Entity<ID> {
    private final ID id;

    protected Entity(ID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public final ID id() { return id; }

    @Override
    public final boolean equals(Object other) {
        return this == other || other != null && getClass() == other.getClass()
                && id.equals(((Entity<?>) other).id);
    }

    @Override
    public final int hashCode() { return Objects.hash(getClass(), id); }
}
