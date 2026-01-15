package dev.protocol;

import dev.message.payload.CircuitCreatePayload;
import dev.models.Message;
import dev.network.CircuitManager;
import dev.network.Peer;
import dev.utils.Logger;

import java.util.UUID;

public class CircuitProtocol implements Protocol {
    private final Logger logger;
    private final CircuitManager circuitManager;

    public CircuitProtocol(CircuitManager circuitManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.circuitManager = circuitManager;
    }

    @Override
    public void digest(Peer peer, Message message) {
        switch (message.getMessageType()) {
            case CIRCUIT_CREATE_REQUEST:
                handleCircuitCreateRequest(peer, message);
                break;
            case CIRCUIT_CREATE_RESPONSE:
                handleCircuitCreateResponse(peer, message);
                break;
            case CIRCUIT_EXTEND_REQUEST:
                handleCircuitExtendRequest(peer, message);
                break;
            case CIRCUIT_EXTEND_RESPONSE:
                handleCircuitExtendResponse(peer, message);
                break;
            default:
                logger.warn("CircuitProtocol received unexpected message type: {}", message.getMessageType());
        }
    }

    private void handleCircuitCreateRequest(Peer peer, Message message) {
        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();
        circuitManager.onCircuitCreateRequest(peer, circuitId, payload);
    }

    private void handleCircuitCreateResponse(Peer peer, Message message) {
        circuitManager.onCircuitCreateResponse(peer, message);
    }

    private void handleCircuitExtendRequest(Peer peer, Message message) {
        circuitManager.onCircuitExtendRequest(peer, message);
    }

    private void handleCircuitExtendResponse(Peer peer, Message message) {
        circuitManager.onCircuitExtendResponse(peer, message);
    }

}
