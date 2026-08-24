package com.alcatraz.exception;

public class GameAlreadyStartedException extends RuntimeException {
    public GameAlreadyStartedException() {
        super();
    }

    public GameAlreadyStartedException(String message) {
        super(message);
    }
}
