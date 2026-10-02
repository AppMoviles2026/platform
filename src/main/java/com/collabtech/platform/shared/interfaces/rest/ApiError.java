package com.collabtech.platform.shared.interfaces.rest;

import java.util.Map;

/** Proposed error response; translation to HTTP belongs to the future REST adapter. */
public record ApiError(String code, String message, Map<String, String> fieldErrors) {
    public ApiError { fieldErrors = Map.copyOf(fieldErrors); }
}
