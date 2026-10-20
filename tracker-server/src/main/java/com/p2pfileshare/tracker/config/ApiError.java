package com.p2pfileshare.tracker.config;

import java.time.Instant;
import java.util.List;
public record ApiError(Instant timestamp, int status, String error, String message, String path, List<FieldError> fieldErrors) {
    public record FieldError(String field, String message) {}
}
