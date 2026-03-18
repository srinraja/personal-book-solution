package com.example.demo.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Global exception handler for the application.
 * Intercepts various exceptions and maps them to a consistent {@link ErrorResponse} structure.
 */
@RestControllerAdvice
@Slf4j
public class BookApiExceptionHandler {

    /**
     * Handles specific retrieval errors from the Google Books logic.
     */
    @ExceptionHandler(GoogleBookRetrievalException.class)
    public ResponseEntity<ErrorResponse> handleGoogleBookRetrievalException(GoogleBookRetrievalException ex) {
        log.error("Google Book Retrieval Error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles explicit Spring ResponseStatusExceptions (thrown for duplicates, invalid IDs, etc.).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("API Error ({}): {}", ex.getStatusCode(), ex.getReason());
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return buildResponse(HttpStatus.valueOf(ex.getStatusCode().value()), message);
    }

    /**
     * Handles cases where a URL path variable or parameter doesn't match the expected type,
     * or when a required path variable is missing.
     */
    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingPathVariableException.class,
            org.springframework.web.bind.ServletRequestBindingException.class
    })
    public ResponseEntity<ErrorResponse> handleBindingException(Exception ex) {
        log.error("Binding Error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request parameters or path variables");
    }

    /**
     * Handles 404 No Resource Found (New in Spring Boot 3.2).
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        log.error("Resource Not Found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "The requested resource was not found");
    }

    /**
     * Handles 405 Method Not Allowed.
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        log.error("Method Not Supported: {}", ex.getMessage());
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    /**
     * Handles errors from the RestClient (Google Books API calls).
     */
    @ExceptionHandler(org.springframework.web.client.RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClientException(org.springframework.web.client.RestClientException ex) {
        log.error("Downstream API Error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_GATEWAY, "Error communicating with the external Google Books API");
    }

    /**
     * Catch-all handler for any unexpected internal server errors.
     * Prevents internal details from leaking to the API client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected Internal Error: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }
}
