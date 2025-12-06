package dev.message.payload;

import dev.network.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PeerRequestPayload extends MessagePayload{
    private List<PeerInfo> peerList;
}
