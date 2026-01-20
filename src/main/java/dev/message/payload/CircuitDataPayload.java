package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitDataPayload extends MessagePayload {
    public UUID circuitId;
    public String host;
    public String port;
    public byte[] data;

    public CircuitDataPayload(byte[] bytes) {
        this.fromBytes(bytes);
    }

    @Override
    public byte[] toBytes() {
        String serialized = this.circuitId + "|" + this.host + "|" + this.port + "|";
        byte[] serializedBytes = serialized.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(4 + serializedBytes.length + this.data.length);

        buffer.putInt(serializedBytes.length);
        buffer.put(serializedBytes);
        buffer.put(this.data);

        return buffer.array();
    }

    @Override
    public void fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        int metadataLength = buffer.getInt();
        byte[] metadataBytes = new byte[metadataLength];
        buffer.get(metadataBytes);

        String serialized = new String(metadataBytes, StandardCharsets.UTF_8);
        String[] parts = serialized.split("\\|", 4);

        this.circuitId = UUID.fromString(parts[0]);
        this.host = parts[1];
        this.port = parts[2];

        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        this.data = data;
    }
}