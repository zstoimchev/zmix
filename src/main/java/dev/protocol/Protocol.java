package dev.protocol;

import dev.message.Message;
import dev.network.Peer;

public interface Protocol {
    void digest(Peer peer, Message message);
}
