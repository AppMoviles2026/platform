package com.collabtech.platform.shared.interfaces.rest;

import java.util.Map;

/** Reusable error response; translation from context-specific failures belongs to REST adapters. */
public record ApiError(String code, String message, Map<String, String> fieldErrors) {
    public ApiError { fieldErrors = Map.copyOf(fieldErrors); }
}
