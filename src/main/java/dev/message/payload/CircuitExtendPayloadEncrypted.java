package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitExtendPayloadEncrypted extends MessagePayload{
    private final UUID circuitId;
    private final byte[] encryptedData;

    @Override
    public byte[] toBytes() {
        byte[] circuitIdBytes = this.circuitId.toString().getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(4 + circuitIdBytes.length + encryptedData.length);

        buffer.putInt(circuitIdBytes.length);
        buffer.put(circuitIdBytes);
        buffer.put(encryptedData);

        return buffer.array();
    }

    public static CircuitExtendPayloadEncrypted fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int circuitIdLength = buffer.getInt();
        byte[] circuitIdBytes = new byte[circuitIdLength];
        buffer.get(circuitIdBytes);
        UUID circuitId = UUID.fromString(new String(circuitIdBytes));
        byte[] encryptedData = new byte[buffer.remaining()];
        buffer.get(encryptedData);
        return new CircuitExtendPayloadEncrypted(circuitId, encryptedData);
    }
}
