package dev.message;

import dev.message.payload.*;
import dev.models.enums.MessageType;
import dev.models.Message;
import dev.models.PeerInfo;
import dev.utils.CustomException;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class MessageSerializer {
    private static final String delimiter = ";delim;;;;";

    public static String serialize(Message message) {
        return message.getMessageType() + delimiter +
                message.getTimestamp() + delimiter +
                message.getMessageId() + delimiter +
                serializePayload(message.getMessageType(), message.getPayload());
    }

    private static String serializePayload(MessageType messageType, MessagePayload payload) {
        switch (messageType) {

            case HANDSHAKE -> {
                if (!(payload instanceof HandshakePayload hp))
                    throw new CustomException("Expected HandshakePayload", null);
                return hp.getPublicKeyBase64Encoded();
            }

            case PEER_DISCOVERY_REQUEST -> {
                return "";
            }

            case PEER_DISCOVERY_RESPONSE -> {
                if (!(payload instanceof PeerResponsePayload prp)) {
                    throw new CustomException("Expected PeerResponsePayload", null);
                }

                StringBuilder sb = new StringBuilder();
                List<PeerInfo> peers = prp.getPeerList();

                sb.append(peers.size());
                for (PeerInfo peer : peers) {
                    sb.append("#")
                            .append(peer.getPublicKey())
                            .append("@")
                            .append(peer.getHost())
                            .append(":")
                            .append(peer.getPort());
                }
                return sb.toString();
            }

            case CIRCUIT_CREATE_REQUEST, CIRCUIT_CREATE_RESPONSE -> {
                if (!(payload instanceof CircuitCreatePayload ccr)) {
                    throw new CustomException("Expected CircuitCreatePayload", null);
                }
                return ccr.getCircuitId().toString() + "@" + ccr.getEphemeralKey();
            }

            case CIRCUIT_EXTEND_REQUEST, CIRCUIT_EXTEND_RESPONSE -> {
                if (!(payload instanceof CircuitExtendPayloadEncrypted cer)) {
                    throw new CustomException("Expected CircuitExtendEncryptedPayload", null);
                }
                String base64Data = java.util.Base64.getEncoder().encodeToString(cer.getEncryptedData());
                return cer.getCircuitId().toString() + "@" + base64Data;
            }

            default -> throw new CustomException("Unexpected value: " + payload, null);
        }
    }

    public static Message deserialize(String rawString) {
        if (rawString == null || rawString.isEmpty()) return null;

        String[] parts = rawString.split(Pattern.quote(delimiter), -1);

        MessageType messageType = MessageType.valueOf(parts[0]);
        long timestamp = Long.parseLong(parts[1]);
        String messageId = parts[2];
        MessagePayload payload = deserializePayload(messageType, parts[3]);

        return new Message(
                messageType,
                timestamp,
                messageId,
                payload
        );
    }

    private static MessagePayload deserializePayload(MessageType messageType, String rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) return null;

        switch (messageType) {

            case HANDSHAKE -> {
                return new HandshakePayload(rawPayload);
            }

            case PEER_DISCOVERY_REQUEST -> {
                return null;
            }

            case PEER_DISCOVERY_RESPONSE -> {
                String[] peerParts = rawPayload.split("#");
                int peerCount = Integer.parseInt(peerParts[0]);
                List<PeerInfo> peerList = new java.util.ArrayList<>();

                for (int i = 1; i <= peerCount; i++) {
                    String[] infoParts = peerParts[i].split("@");
                    String publicKey = infoParts[0];
                    String[] hostPort = infoParts[1].split(":");
                    String host = hostPort[0];
                    int port = Integer.parseInt(hostPort[1]);

                    peerList.add(new PeerInfo(publicKey, host, port));
                }
                return new PeerResponsePayload(peerList);
            }

            case CIRCUIT_CREATE_REQUEST, CIRCUIT_CREATE_RESPONSE -> {
                String[] ccrParts = rawPayload.split("@");
                UUID circuitId = UUID.fromString(ccrParts[0]);
                String secretKey = ccrParts[1];
                return new CircuitCreatePayload(circuitId, secretKey);
            }

            case CIRCUIT_EXTEND_REQUEST, CIRCUIT_EXTEND_RESPONSE -> {
                String[] parts = rawPayload.split("@", 2);
                UUID circuitId = UUID.fromString(parts[0]);
                byte[] encryptedData = java.util.Base64.getDecoder().decode(parts[1]);
                return new CircuitExtendPayloadEncrypted(circuitId, encryptedData);
            }

            default -> throw new CustomException("Unexpected value: " + messageType, null);
        }
    }
}
