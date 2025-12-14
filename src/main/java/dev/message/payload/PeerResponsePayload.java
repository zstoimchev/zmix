package dev.message.payload;

import dev.network.peer.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PeerResponsePayload extends MessagePayload{
    private final List<PeerInfo> peerList;
}
