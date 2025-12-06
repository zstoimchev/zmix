package dev.protocol;

import dev.message.Message;
import dev.network.Peer;

public class HandshakeProtocol implements Protocol {
    @Override
    public void digest(Peer peer, Message message) {
        // TODO: switch header.protocol.handshake.type or body type to appropriate handler
    }
}
