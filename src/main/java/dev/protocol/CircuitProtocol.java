package dev.protocol;

import dev.message.MessageBuilder;
import dev.message.payload.CircuitCreatePayload;
import dev.models.Message;
import dev.network.CircuitManager;
import dev.network.NetworkManager;
import dev.network.Peer;
import dev.utils.Crypto;
import dev.utils.Logger;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;

public class CircuitProtocol implements Protocol {
    private final Logger logger;
    private final CircuitManager circuitManager;
    private final Crypto crypto;

    public CircuitProtocol(CircuitManager circuitManager, NetworkManager networkManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.circuitManager = circuitManager;
        this.crypto = networkManager.getCrypto();
    }

    @Override
    public void digest(Peer peer, Message message) {
        switch (message.getMessageType()) {
            case CIRCUIT_CREATE_REQUEST:
                handleCircuitCreateRequest(peer, message);
                break;
            case CIRCUIT_CREATE_RESPONSE:
                circuitManager.onCircuitCreateResponse(peer, message);
                break;
            default:
                logger.warn("PeerDiscoveryProtocol received unexpected message type: {}", message.getMessageType());
        }
    }

    private void handleCircuitCreateRequest(Peer peer, Message message) {
        logger.info("Received CIRCUIT_CREATE_REQUEST from peer: {}", peer.getPeerId());

        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        KeyPair ephemeralKeyPair = crypto.generateECDHKeyPair();

        // 2. Decode requester's ephemeral public key
        PublicKey theirEphemeralPublicKey = crypto.decodePublicKey(payload.getSecretKey());

        // 3. Perform ECDH to derive shared secret
        byte[] sharedSecret = crypto.performECDH(ephemeralKeyPair.getPrivate(), theirEphemeralPublicKey);

        // 4. Derive session key
        byte[] sessionKey = crypto.deriveAESKey(sharedSecret);

        // 5. Store circuit relay info
        // TODO: Create CircuitRelay class to store: circuitId, previousHop (peer), sessionKey, nextHop (null for now)
        // relayCircuits.put(circuitId, new CircuitRelay(circuitId, peer, sessionKey, null));

        // 6. Send CIRCUIT_CREATED response with our ephemeral public key
        String ourEphemeralKeyBase64 = Base64.getEncoder().encodeToString(ephemeralKeyPair.getPublic().getEncoded());
        Message response = MessageBuilder.buildCircuitCreateMessageResponse(circuitId, ourEphemeralKeyBase64);
        peer.send(response);

        logger.info("Sent CIRCUIT_CREATED for circuit: {}", circuitId);

    }

    private void handleCircuitCreateResponse(Peer peer, Message message) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
