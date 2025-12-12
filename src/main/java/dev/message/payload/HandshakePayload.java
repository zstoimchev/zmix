package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HandshakePayload extends MessagePayload {
    private String publicKeyBase64Encoded; // Base64 encoded public key
}

