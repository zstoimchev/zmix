package dev.protocol;

import dev.models.Message;
import dev.network.Peer;

public interface Protocol {
    void digest(Peer peer, Message message);
}
