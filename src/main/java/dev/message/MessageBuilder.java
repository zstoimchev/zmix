package dev.message;

import dev.message.payload.HandshakePayload;
import dev.message.payload.PeerRequestPayload;
import dev.network.PeerInfo;

import java.util.List;
import java.util.UUID;

public class MessageBuilder {

    public Message buildHandshakeMessage(String senderPublicKeyEncoded) {
        return new Message(
                MessageType.HANDSHAKE,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new HandshakePayload(senderPublicKeyEncoded)
        );
    }

    public Message buildPeerRequestMessage() {
        return new Message(
                MessageType.PEER_DISCOVERY_REQUEST,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                null
        );
    }

    public Message buildPeerResponseMessage(List<PeerInfo> peerList) {
        return new Message(
                MessageType.PEER_DISCOVERY_RESPONSE,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new PeerRequestPayload(peerList)
        );
    }

    public Message buildCircuitCreateMessageRequest(String circuitId, String nextHopPublicKey) {
        return new Message(
                MessageType.CIRCUIT_CREATE_REQUEST,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                null
        );
    }

    public Message buildCircuitCreateMessageResponse(String circuitId, String nextHopPublicKey) {
        return new Message(
                MessageType.CREATE_CIRCUIT_RESPONSE,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                null
        );
    }
}
