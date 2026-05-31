package dev.message;

import dev.message.payload.*;
import dev.models.enums.MessageType;
import dev.models.Message;
import dev.exceptions.CustomException;
import dev.utils.Utils;

import java.util.regex.Pattern;

public class MessageSerializer {
    private static final String delimiter = ";delim;;;;";

    public static String serialize(Message message) {
        return message.getMessageType() + delimiter + message.getTimestamp() + delimiter + message.getMessageId() + delimiter + serializePayload(message.getMessageType(), message.getPayload());
    }

    private static String serializePayload(MessageType messageType, MessagePayload payload) {
        switch (messageType) {

            case HANDSHAKE -> {
                if (!(payload instanceof HandshakePayload hp))
                    throw new CustomException("Expected HandshakePayload", null);

                byte[] bytes = hp.toBytes();
                return Utils.encodeBytesToString(bytes);
            }

            case PEER_DISCOVERY_REQUEST -> {
                return "";
            }

            case PEER_DISCOVERY_RESPONSE -> {
                if (!(payload instanceof PeerResponsePayload prp))
                    throw new CustomException("Expected PeerResponsePayload", null);

                byte[] bytes = prp.toBytes();
                return Utils.encodeBytesToString(bytes);
            }

            case CIRCUIT_CREATE_REQUEST, CIRCUIT_CREATE_RESPONSE -> {
                if (!(payload instanceof CircuitCreatePayload ccr))
                    throw new CustomException("Expected CircuitCreatePayload", null);

                byte[] bytes = ccr.toBytes();
                return Utils.encodeBytesToString(bytes);
            }

            case CIRCUIT_EXTEND_REQUEST, CIRCUIT_EXTEND_RESPONSE -> {
                if (!(payload instanceof CircuitExtendPayloadEncrypted cer))
                    throw new CustomException("Expected CircuitExtendEncryptedPayload", null);

                byte[] bytes = cer.toBytes();
                return Utils.encodeBytesToString(bytes);
            }

            case DATA_TRANSFER_REQUEST, DATA_TRANSFER_RESPONSE -> {
                if (!(payload instanceof CircuitDataPayload cdp))
                    throw new CustomException("Expected DataTransferPayload", null);

                byte[] bytes = cdp.toBytes();
                return Utils.encodeBytesToString(bytes);
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

        return new Message(messageType, timestamp, messageId, payload);
    }

    private static MessagePayload deserializePayload(MessageType messageType, String rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) return null;
        byte[] data = Utils.decodeStringToBytes(rawPayload);

        return switch (messageType) {
            case HANDSHAKE -> HandshakePayload.fromBytes(data);
            case PEER_DISCOVERY_REQUEST -> null;
            case PEER_DISCOVERY_RESPONSE -> PeerResponsePayload.fromBytes(data);
            case CIRCUIT_CREATE_REQUEST, CIRCUIT_CREATE_RESPONSE -> CircuitCreatePayload.fromBytes(data);
            case CIRCUIT_EXTEND_REQUEST, CIRCUIT_EXTEND_RESPONSE -> CircuitExtendPayloadEncrypted.fromBytes(data);
            case DATA_TRANSFER_REQUEST, DATA_TRANSFER_RESPONSE -> CircuitDataPayload.fromBytes(data);
            default -> throw new CustomException("Unexpected value: " + messageType, null);
        };
    }
}
