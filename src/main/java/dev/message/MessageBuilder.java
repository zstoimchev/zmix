package dev.message;

import dev.message.payload.PeerRequestPayload;
import dev.network.PeerInfo;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
public class MessageBuilder {
    private String senderPublicKey;
    private UUID senderNodeId;

    public Message buildHandshakeMessage() {
        return new Message(
                MessageType.HANDSHAKE,
                senderPublicKey,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                null
        );
    }

    public Message buildPeerRequestMessage() {
        return new Message(
                MessageType.PEER_REQUEST,
                senderPublicKey,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                null
        );
    }

    public Message buildPeerResponseMessage(List<PeerInfo> peerList) {
        return new Message(
                MessageType.PEER_RESPONSE,
                senderPublicKey,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new PeerRequestPayload(peerList)
        );
    }

    public Message buildCircuitCreateMessage(String circuitId, String nextHopPublicKey) {
        return new Message(
                MessageType.CIRCUIT_CREATE,
                senderPublicKey,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                circuitId,
                nextHopPublicKey
        );
    }
}
