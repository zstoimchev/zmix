package dev.message;

import dev.message.payload.*;
import dev.models.enums.MessageType;
import dev.models.enums.PayloadType;
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

    public static Message buildCircuitCreateMessageRequest(UUID circuitId, String secretKey) {
        return new Message(
                MessageType.CIRCUIT_CREATE_REQUEST,
                PayloadType.CIRCUIT_CREATE_REQUEST,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new CircuitCreatePayload(circuitId, secretKey)
        );
    }

    public static Message buildCircuitCreateMessageResponse(UUID circuitId, String secretKey) {
        return new Message(
                MessageType.CIRCUIT_CREATE_RESPONSE,
                PayloadType.CIRCUIT_CREATE_RESPONSE,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new CircuitCreatePayload(circuitId, secretKey)
        );
    }

    public static Message buildCircuitExtendMessageRequest(UUID circuitId, byte[] data) {
        return new Message(
                MessageType.CIRCUIT_EXTEND_REQUEST,
                PayloadType.CIRCUIT_EXTEND_REQUEST,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new CircuitExtendEncryptedPayload(circuitId, data)
        );
    }

    public static Message buildCircuitExtendMessageResponse(UUID circuitId, byte[] data) {
        return new Message(
                MessageType.CIRCUIT_EXTEND_RESPONSE,
                PayloadType.CIRCUIT_EXTEND_RESPONSE,
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                null,
                new CircuitExtendEncryptedPayload(circuitId, data)
        );
    }
}
