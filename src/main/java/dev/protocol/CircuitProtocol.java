package dev.protocol;

import dev.message.payload.CircuitCreatePayload;
import dev.message.payload.CircuitExtendRequestPayload;
import dev.message.payload.CircuitExtendResponsePayload;
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
        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();
        if (circuitId.equals(circuitManager.getMyCircuitId())) {
            circuitManager.onCircuitCreateResponse(peer, circuitId, payload);
            return;
        }
        circuitManager.onCircuitCreatedResponseExtended(peer, circuitId, payload);
    }

    private void handleCircuitExtendRequest(Peer peer, Message message) {
        CircuitExtendRequestPayload payload = (CircuitExtendRequestPayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        // Get the

    }

    private void handleCircuitExtendResponse(Peer peer, Message message) {
        CircuitExtendResponsePayload payload = (CircuitExtendResponsePayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();
        if (circuitId == circuitManager.getMyCircuitId()) {
            // we received response that we extended the circuit to the next hop.
            // Decrypt the payload with all the keys that we have up until now, get the eph key, and save it
        }
        // we receive that someone extended the circuit further.
        // it is not our circuit so we need to send the response back.
        // encrypt the payload, and send back to previous hop
    }

}
