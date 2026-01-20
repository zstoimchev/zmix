package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HandshakePayload extends MessagePayload {
    private String publicKeyBase64Encoded; // Base64 encoded public key
    private int port;

    public HandshakePayload(byte[] bytes) {
        fromBytes(bytes);
    }

    @Override
    public byte[] toBytes() {
        return (publicKeyBase64Encoded + ":" + port).getBytes();
    }

    @Override
    public void fromBytes(byte[] bytes) {
        String raw = new String(bytes);
        String[] parts = raw.split(":");

        if (parts.length == 2) {
            this.publicKeyBase64Encoded = parts[0];
            this.port = Integer.parseInt(parts[1]);
        }
    }
}
