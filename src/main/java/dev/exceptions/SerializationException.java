package dev.exceptions;

import dev.models.enums.MessageType;

public class SerializationException extends ZmixBaseException {
    public SerializationException(MessageType messageType, Class<?> expectedPayload, Object actualPayload) {
        super("Payload serialization error for message " + messageType + ". " +
                "Expected " + expectedPayload.getSimpleName() + ", " +
                "but got " + (actualPayload == null ? "null" : actualPayload.getClass().getSimpleName()));
    }
}