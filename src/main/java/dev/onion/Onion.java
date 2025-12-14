package dev.onion;

import dev.message.enums.MessageType;

import java.util.UUID;

public class Onion {
    private UUID circuitId;
    private MessageType type;
    private OnionPayload payload;
}
