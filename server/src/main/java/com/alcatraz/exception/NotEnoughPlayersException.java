package com.alcatraz.exception;

public class NotEnoughPlayersException extends RuntimeException {
    public NotEnoughPlayersException() {
        super();
    }

    public NotEnoughPlayersException(String message) {
        super(message);
    }
}
