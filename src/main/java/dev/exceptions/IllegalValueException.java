package dev.exceptions;

public class IllegalValueException extends ZmixBaseException {
    public IllegalValueException(String message) {
        super(message);
    }

    public IllegalValueException(String message, Throwable cause) {
        super(message, cause);
    }
}