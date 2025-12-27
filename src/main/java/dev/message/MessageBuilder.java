package dev.message;

import dev.models.enums.MessageType;
import dev.models.enums.PayloadType;
import dev.message.payload.HandshakePayload;
import dev.message.payload.PeerResponsePayload;
import dev.models.Message;
import dev.models.PeerInfo;

import java.util.List;
import java.util.UUID;

public class MessageBuilder {

    public static Message buildHandshakeMessage(String senderPublicKeyEncoded) {
        return new Message(
                MessageType.HANDSHAKE,
                PayloadType.HANDSHAKE,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new HandshakePayload(senderPublicKeyEncoded)
        );
    }

    public static Message buildPeerRequestMessage() {
        return new Message(
                MessageType.PEER_DISCOVERY_REQUEST,
                PayloadType.PEER_REQUEST,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                null
        );
    }

    public static Message buildPeerResponseMessage(List<PeerInfo> peerList) {
        return new Message(
                MessageType.PEER_DISCOVERY_RESPONSE,
                PayloadType.PEER_RESPONSE,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new PeerResponsePayload(peerList)
        );
    }

    public static Message buildCircuitCreateMessageRequest(String circuitId, String nextHopPublicKey) {
        return new Message(
                MessageType.CIRCUIT_CREATE_REQUEST,
                null,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                null
        );
    }

    public static Message buildCircuitCreateMessageResponse(String circuitId, String nextHopPublicKey) {
        return new Message(
                MessageType.CREATE_CIRCUIT_RESPONSE,
                null,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                null
        );
    }
}
