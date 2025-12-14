package dev.onion;

import dev.network.peer.Peer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Circuit {
    private final String circuitId;
    private final List<Peer> path;
}

