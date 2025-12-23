package dev.message;

import dev.message.enums.MessageType;
import dev.message.enums.PayloadType;
import dev.message.payload.HandshakePayload;
import dev.message.payload.MessagePayload;
import dev.message.payload.PeerResponsePayload;
import dev.network.peer.PeerInfo;
import dev.utils.CustomException;

import java.util.List;
import java.util.regex.Pattern;

public class MessageSerializer {
    private static final String delimiter = ";delim;;;;";

    public static String serialize(Message message) {
        return message.getMessageType() + delimiter +
                message.getPayloadType() + delimiter +
                message.getTimestamp() + delimiter +
                message.getMessageId() + delimiter +
                message.getSignature() + delimiter +
                serializePayload(message.getPayloadType(), message.getPayload());
    }

    private static String serializePayload(PayloadType payloadType, MessagePayload payload) {
        switch (payloadType) {
            case HANDSHAKE -> {
                if (!(payload instanceof HandshakePayload hp))
                    throw new CustomException("Expected HandshakePayload", null);
                return hp.getPublicKeyBase64Encoded();
            }
            case PEER_REQUEST -> {
                return "";
            }
            case PEER_RESPONSE -> {
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
            default -> throw new CustomException("Unexpected value: " + payload, null);
        }
    }

    public static Message deserialize(String rawString) {
        if (rawString == null || rawString.isEmpty()) return null;

        String[] parts = rawString.split(Pattern.quote(delimiter), -1);

        MessageType messageType = MessageType.valueOf(parts[0]);
        PayloadType payloadType = PayloadType.valueOf(parts[1]);
        long timestamp = Long.parseLong(parts[2]);
        String messageId = parts[3];
        String signature = parts[4];
        MessagePayload payload = deserializePayload(payloadType, parts[5]);

        return new Message(
                messageType,
                payloadType,
                timestamp,
                messageId,
                signature,
                payload
        );
    }

    private static MessagePayload deserializePayload(PayloadType payloadType, String rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) return null;

        switch (payloadType) {
            case HANDSHAKE -> {
                return new HandshakePayload(rawPayload);
            }
            case PEER_REQUEST -> {
                return null;
            }
            case PEER_RESPONSE -> {
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
            default -> throw new CustomException("Unexpected value: " + payloadType, null);
        }
    }
}
