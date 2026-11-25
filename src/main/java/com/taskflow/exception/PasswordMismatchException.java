package com.taskflow.exception;

/**
 * Raised when password and confirm password do not match during registration.
 */
public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException(String message) {
        super(message);
    }
}
