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

    // GPT explained why standard parsing will fail and why data length is needed
    public static CircuitDataPayload fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        int metadataLength = buffer.getInt();
        byte[] metadataBytes = new byte[metadataLength];
        buffer.get(metadataBytes);

        String serialized = new String(metadataBytes, StandardCharsets.UTF_8);
        String[] parts = serialized.split("\\|", 4);

        UUID id = UUID.fromString(parts[0]);
        String host = parts[1];
        String port = parts[2];

        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        return new CircuitDataPayload(id, host, port, data);
    }
}