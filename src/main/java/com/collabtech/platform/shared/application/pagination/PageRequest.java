package com.collabtech.platform.shared.application.pagination;

public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page >= 0 and size between 1 and 100 required");
        }
    }
}
