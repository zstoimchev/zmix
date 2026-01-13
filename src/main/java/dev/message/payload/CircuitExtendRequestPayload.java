package dev.message.payload;

import lombok.Getter;

import java.util.UUID;

@Getter
public class CircuitExtendRequestPayload extends MessagePayload {
    UUID circuitId;
}
