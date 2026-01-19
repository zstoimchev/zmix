package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitCreatePayload extends MessagePayload {
    public final UUID circuitId;
    public final String ephemeralKey;

    @Override
    public byte[] toBytes() {
        return (circuitId + "|" + ephemeralKey).getBytes();
    }

    public static CircuitCreatePayload fromBytes(byte[] bytes) {
        String raw = new String(bytes);
        String[] parts = raw.split("\\|", 2);

        if (parts.length == 2) {
            UUID id = UUID.fromString(parts[0]);
            String ephKey = parts[1];
            return new CircuitCreatePayload(id, ephKey);
        }

        return null;
    }
}
