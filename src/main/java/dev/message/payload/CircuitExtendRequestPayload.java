package dev.message.payload;

import dev.models.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitExtendRequestPayload extends MessagePayload {
    private UUID circuitId;
    private PeerInfo peerInfo;
    private String ephemeralKey;

    public CircuitExtendRequestPayload(byte[] bytes) {
        this.fromBytes(bytes);
    }

    @Override
    public byte[] toBytes() {
        byte[] uuidBytes = circuitId.toString().getBytes();
        byte[] peerBytes = peerInfo.serialize().getBytes();
        byte[] ephBytes = ephemeralKey.getBytes();

        ByteBuffer buffer = ByteBuffer.allocate(
                4 + uuidBytes.length +
                        4 + peerBytes.length +
                        4 + ephBytes.length);

        buffer.putInt(uuidBytes.length);
        buffer.put(uuidBytes);

        buffer.putInt(peerBytes.length);
        buffer.put(peerBytes);

        buffer.putInt(ephBytes.length);
        buffer.put(ephBytes);

        return buffer.array();
    }

    @Override
    public void fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int uuidLength = buffer.getInt();
        byte[] uuidBytes = new byte[uuidLength];
        buffer.get(uuidBytes);
        this.circuitId = UUID.fromString(new String(uuidBytes));

        int peerLength = buffer.getInt();
        byte[] peerBytes = new byte[peerLength];
        buffer.get(peerBytes);
        this.peerInfo = PeerInfo.deserialize(new String(peerBytes));

        int ephLength = buffer.getInt();
        byte[] ephBytes = new byte[ephLength];
        buffer.get(ephBytes);
        this.ephemeralKey = new String(ephBytes);
    }
}
