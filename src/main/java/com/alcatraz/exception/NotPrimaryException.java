package com.alcatraz.exception;

public class NotPrimaryException extends RuntimeException {
    public NotPrimaryException() {
        super();
    }

    public NotPrimaryException(String message) {
        super(message);
    }
}
