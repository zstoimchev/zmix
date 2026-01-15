package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitExtendPayloadEncrypted extends MessagePayload{
    private final UUID circuitId;
    private final byte[] encryptedData;
}
