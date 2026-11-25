package com.alcatraz.client;

final class RequestException extends RuntimeException {
    final int status;

    RequestException(int status, String message) {
        super(message);
        this.status = status;
    }
}
