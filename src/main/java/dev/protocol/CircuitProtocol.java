package dev.protocol;

import dev.message.payload.CircuitCreatePayload;
import dev.models.Message;
import dev.network.CircuitManager;
import dev.network.Peer;
import dev.utils.Logger;

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
                circuitManager.onCircuitCreateRequest(peer, message);
                break;

            case CIRCUIT_CREATE_RESPONSE:
                CircuitCreatePayload createPayload = (CircuitCreatePayload) message.getPayload();
                if (circuitManager.getCircuitId() == createPayload.getCircuitId()) {
                    circuitManager.onCircuitCreateResponse(peer, message);
                } else {
                    circuitManager.onCircuitExtendResponseAsRelay(peer, message);
                }
                break;

            case CIRCUIT_EXTEND_REQUEST:
                circuitManager.onCircuitExtendRequest(peer, message);
                break;

            case CIRCUIT_EXTEND_RESPONSE:
                circuitManager.onCircuitExtendResponse(peer, message);
                break;

            default:
                logger.warn("CircuitProtocol received unexpected message type: {}", message.getMessageType());
        }
    }

}
