package dev.protocol;

import dev.models.Message;
import dev.network.Peer;
import dev.utils.Logger;

public class CircuitProtocol implements Protocol {
    private final Logger logger;

    public CircuitProtocol() {
        this.logger = Logger.getLogger(this.getClass());
    }


    @Override
    public void digest(Peer peer, Message message) {
        switch (message.getMessageType()) {
            case CIRCUIT_CREATE_REQUEST:
                handleCircuitCreateRequest(peer, message);
                break;
            case CREATE_CIRCUIT_RESPONSE:
                handleCircuitCreateResponse(peer, message);
                break;
            default:
                logger.warn("PeerDiscoveryProtocol received unexpected message type: {}", message.getMessageType());
        }
    }

    private void handleCircuitCreateResponse(Peer peer, Message message) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void handleCircuitCreateRequest(Peer peer, Message message) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
