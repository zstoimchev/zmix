package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HandshakePayload extends MessagePayload {
    private final String publicKeyEncoded;
    private final int port;

    @Override
    public byte[] toBytes() {
        return (publicKeyEncoded + ":" + port).getBytes();
    }

    public static HandshakePayload fromBytes(byte[] bytes) {
        String raw = new String(bytes);
        String[] parts = raw.split(":");

        if (parts.length == 2) {
            String publicKey = parts[0];
            int port = Integer.parseInt(parts[1]);
            return new HandshakePayload(publicKey, port);
        }

        return null;
    }
}
