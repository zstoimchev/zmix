package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HandshakePayload extends MessagePayload {
    private final String publicKeyBase64Encoded; // Base64 encoded public key
    private final int port;
}
