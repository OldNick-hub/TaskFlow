package com.taskflow.exception;

/**
 * Raised when a JWT access token is malformed, expired, invalid, or revoked.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
