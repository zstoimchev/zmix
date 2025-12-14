package dev.message.payload;

import dev.network.PeerInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PeerResponsePayload extends MessagePayload{
    private List<PeerInfo> peerList;
}
