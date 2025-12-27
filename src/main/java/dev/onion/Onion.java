package dev.onion;

import dev.models.enums.MessageType;

import java.util.UUID;

public class Onion {
    private UUID circuitId;
    private MessageType type;
    private OnionPayload payload;
}
