package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitCreatePayload extends MessagePayload {
    public UUID circuitId;
    public String ephemeralKey;

    public CircuitCreatePayload(byte[] bytes) {
        this.fromBytes(bytes);
    }

    @Override
    public byte[] toBytes() {
        return (circuitId + "|" + ephemeralKey).getBytes();
    }

    @Override
    public void fromBytes(byte[] bytes) {
        String raw = new String(bytes);
        String[] parts = raw.split("\\|", 2);

        if (parts.length == 2) {
            this.circuitId = UUID.fromString(parts[0]);
            this.ephemeralKey = parts[1];
        }
    }
}
