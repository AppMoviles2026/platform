package com.collabtech.platform.identity.interfaces.rest.resources;

import java.util.UUID;

/** Public account summary, without credentials; registration alone does not grant a session. */
public record AccountResource(UUID accountId, UUID profileId, String name, String accountType, String status) {}
