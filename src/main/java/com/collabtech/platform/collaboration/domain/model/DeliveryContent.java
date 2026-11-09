package com.collabtech.platform.collaboration.domain.model;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Stored references only; the server never downloads or infers ownership of an external URL. */
public record DeliveryContent(Reference content, List<Reference> evidence, String notes) {
    public DeliveryContent {
        Objects.requireNonNull(content, "Content is required");
        evidence = List.copyOf(evidence);
        if (evidence.isEmpty() || evidence.size() > 10 || evidence.stream().distinct().count() != evidence.size()) {
            throw new IllegalArgumentException("Supply one to ten distinct evidence references");
        }
        notes = notes == null ? null : notes.strip();
        if (notes != null && notes.length() > 4000) throw new IllegalArgumentException("Notes exceed 4000 characters");
    }

    public record Reference(String url, UUID assetId) {
        public Reference {
            if ((url == null) == (assetId == null)) throw new IllegalArgumentException("Use either a URL or an asset ID");
            if (url != null) {
                url = url.strip();
                if (url.length() > 2048) throw new IllegalArgumentException("URL is too long");
                var uri = URI.create(url);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("A complete HTTPS URL without embedded credentials is required");
                }
            }
        }
    }

    public List<UUID> assetIds() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(content), evidence.stream())
                .map(Reference::assetId).filter(Objects::nonNull).distinct().toList();
    }
}
