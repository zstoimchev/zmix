package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;

@Getter
@AllArgsConstructor
public class CircuitExtendPayload extends MessagePayload {
    private final String publicKey;
    private final String host;
    private final Integer port;
    private final String ephemeralKey;

    public byte[] toBytes() {
        String serialized = String.format("%s|%s|%d|%s",
                publicKey, host, port, ephemeralKey);
        return serialized.getBytes(StandardCharsets.UTF_8);
    }

    public static CircuitExtendPayload fromBytes(byte[] data) {
        String serialized = new String(data, StandardCharsets.UTF_8);
        String[] parts = serialized.split("\\|");

        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "Invalid CircuitExtendPayload format. Expected 4 parts, got: " + parts.length
            );
        }

        return new CircuitExtendPayload(parts[0], parts[1], Integer.parseInt(parts[2]), parts[3]);
    }
}
