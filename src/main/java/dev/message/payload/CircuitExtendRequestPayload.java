package dev.message.payload;

import dev.models.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitExtendRequestPayload extends MessagePayload {
    private final UUID circuitId;
    private final PeerInfo peerInfo;
    private final String ephemeralKey;

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

    public static CircuitExtendRequestPayload fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int uuidLength = buffer.getInt();
        byte[] uuidBytes = new byte[uuidLength];
        buffer.get(uuidBytes);
        UUID id = UUID.fromString(new String(uuidBytes));

        int peerLength = buffer.getInt();
        byte[] peerBytes = new byte[peerLength];
        buffer.get(peerBytes);
        PeerInfo peer = PeerInfo.deserialize(new String(peerBytes));

        int ephLength = buffer.getInt();
        byte[] ephBytes = new byte[ephLength];
        buffer.get(ephBytes);
        String eph = new String(ephBytes);

        return new CircuitExtendRequestPayload(id, peer, eph);
    }
}
