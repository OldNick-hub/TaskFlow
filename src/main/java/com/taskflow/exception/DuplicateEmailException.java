package com.taskflow.exception;

/**
 * Raised when attempting to register with an email address that already exists.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
