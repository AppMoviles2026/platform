package com.collabtech.platform.identity.interfaces.rest.resources;

import java.util.UUID;

/** Registration result only, not a session or a persistence entity. */
public record AccountResource(UUID accountId, UUID profileId, String name, String accountType, String status) {}
