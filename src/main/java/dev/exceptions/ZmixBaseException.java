package dev.exceptions;

public abstract class ZmixBaseException extends RuntimeException {
    protected ZmixBaseException(String message) {
        super(message);
    }
    protected ZmixBaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
