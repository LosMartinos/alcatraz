package com.alcatraz.exception;

public class ReplicationUnavailableException extends RuntimeException {
    public ReplicationUnavailableException(String message) {
        super(message);
    }

    public ReplicationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
