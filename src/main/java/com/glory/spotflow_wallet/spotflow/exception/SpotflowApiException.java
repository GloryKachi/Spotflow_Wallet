package com.glory.spotflow_wallet.spotflow.exception;

/**
 * Wraps any failure talking to the Spotflow API (network error, 4xx/5xx response, etc.)
 * so callers in core business logic never depend on HTTP-client-specific exceptions.
 */
public class SpotflowApiException extends RuntimeException {

    private final int statusCode;

    public SpotflowApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public SpotflowApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
