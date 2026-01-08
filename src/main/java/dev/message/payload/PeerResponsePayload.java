package dev.message.payload;

import dev.models.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PeerResponsePayload extends MessagePayload{
    private final List<PeerInfo> peerList;

    @Override
    public String toString() {
        return "PeerResponsePayload{" +
                "peerList=" + (peerList == null ? "null" :
                peerList.stream()
                        .map(PeerInfo::toString)
                        .toList()) +
                '}';
    }
}
