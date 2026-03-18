package com.example.demo.exception;

/**
 * DTO for API error responses.
 * Provides a consistent structure for all error messages across the
 * application.
 *
 * @param status    the HTTP status code
 * @param message   a descriptive error message
 * @param timestamp the time the error occurred (milliseconds)
 */
public record ErrorResponse(int status, String message, long timestamp) {
    public ErrorResponse(int status, String message) {
        this(status, message, System.currentTimeMillis());
    }
}
