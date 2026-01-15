package dev.message.payload;

import dev.models.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitExtendRequestPayload extends MessagePayload {
    private final UUID circuitId;
    private final PeerInfo peerInfo;
    private final String ephemeralKey;

    public byte[] toBytes() {
        String serialized = circuitId + "|" + peerInfo.serialize() + "|" + ephemeralKey;
        return serialized.getBytes();
    }

    public static CircuitExtendRequestPayload fromBytes(byte[] data) {
        String serialized = new String(data);
        String[] parts = serialized.split("\\|", 3);
        UUID id = UUID.fromString(parts[0]);
        PeerInfo peer = PeerInfo.deserialize(parts[1]);
        String eph = parts[2];
        return new CircuitExtendRequestPayload(id, peer, eph);
    }
}
