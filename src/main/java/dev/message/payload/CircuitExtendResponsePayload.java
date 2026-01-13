package dev.message.payload;

import lombok.Getter;

import java.util.UUID;

@Getter
public class CircuitExtendResponsePayload extends MessagePayload {
    public UUID circuitId;
}
