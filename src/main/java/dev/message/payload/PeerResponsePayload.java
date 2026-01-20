package dev.message.payload;

import dev.models.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class PeerResponsePayload extends MessagePayload {
    private final List<PeerInfo> peerList;

    @Override
    public byte[] toBytes() {

        String joined = peerList
                .stream()
                .map(PeerInfo::serialize)
                .collect(Collectors.joining("|"));
        return joined.getBytes();
    }

    public static PeerResponsePayload fromBytes(byte[] bytes) {
        if (bytes.length == 0) return new PeerResponsePayload(List.of());

        String raw = new String(bytes);
        List<PeerInfo> peers = Arrays
                .stream(raw.split("\\|"))
                .map(PeerInfo::deserialize)
                .toList();
        return new PeerResponsePayload(peers);
    }
}
