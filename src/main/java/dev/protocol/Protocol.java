package dev.protocol;

import dev.message.Message;
import dev.network.peer.Peer;

public interface Protocol {
    void digest(Peer peer, Message message);
}
