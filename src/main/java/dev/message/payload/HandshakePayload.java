package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HandshakePayload extends MessagePayload {
    private String publicKey;        // Base64 encoded public key
    private String nodeId;           // Unique node identifier
//    private String version;          // Protocol version (e.g., "1.0.0")

    @Override
    public String toString() {
        return "Handshake{nodeId=" + nodeId + "}";
    }
}

