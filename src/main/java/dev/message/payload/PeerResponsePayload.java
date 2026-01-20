package dev.message.payload;

import dev.models.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class PeerResponsePayload extends MessagePayload {
    private List<PeerInfo> peerList;

    public PeerResponsePayload(byte[] bytes) {
        fromBytes(bytes);
    }

    @Override
    public byte[] toBytes() {

        String joined = peerList
                .stream()
                .map(PeerInfo::serialize)
                .collect(Collectors.joining("|"));
        return joined.getBytes();
    }

    @Override
    public void fromBytes(byte[] bytes) {
        if (bytes.length == 0) this.peerList = new ArrayList<>();

        String raw = new String(bytes);
        this.peerList = Arrays
                .stream(raw.split("\\|"))
                .map(PeerInfo::deserialize)
                .toList();
    }
}
