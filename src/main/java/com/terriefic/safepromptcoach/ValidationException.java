package com.terriefic.safepromptcoach;

/** Thrown when a request is invalid. The message is safe to show the user and never contains their task text. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
