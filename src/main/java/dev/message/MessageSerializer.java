package dev.message;

import dev.message.payload.*;
import dev.models.enums.MessageType;
import dev.models.Message;
import dev.exceptions.IllegalValueException;
import dev.exceptions.SerializationException;

import java.util.regex.Pattern;

import static java.util.Base64.getEncoder;

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
                    throw new SerializationException(messageType, HandshakePayload.class, payload);

                byte[] bytes = hp.toBytes();
                return getEncoder().encodeToString(bytes);
            }

            case PEER_DISCOVERY_REQUEST -> {
                return "";
            }

            case PEER_DISCOVERY_RESPONSE -> {
                if (!(payload instanceof PeerResponsePayload prp))
                    throw new SerializationException(messageType, PeerResponsePayload.class, payload);

                byte[] bytes = prp.toBytes();
                return getEncoder().encodeToString(bytes);
            }

            case CIRCUIT_CREATE_REQUEST, CIRCUIT_CREATE_RESPONSE -> {
                if (!(payload instanceof CircuitCreatePayload ccr))
                    throw new SerializationException(messageType, CircuitCreatePayload.class, payload);

                byte[] bytes = ccr.toBytes();
                return getEncoder().encodeToString(bytes);
            }

            case CIRCUIT_EXTEND_REQUEST, CIRCUIT_EXTEND_RESPONSE -> {
                if (!(payload instanceof CircuitExtendPayloadEncrypted cer))
                    throw new SerializationException(messageType, CircuitExtendPayloadEncrypted.class, payload);

                byte[] bytes = cer.toBytes();
                return getEncoder().encodeToString(bytes);
            }

            case DATA_TRANSFER_REQUEST, DATA_TRANSFER_RESPONSE -> {
                if (!(payload instanceof CircuitDataPayload cdp))
                    throw new SerializationException(messageType, CircuitDataPayload.class, payload);

                byte[] bytes = cdp.toBytes();
                return getEncoder().encodeToString(bytes);
            }

            default -> throw new IllegalValueException("Unexpected MessageType: " + messageType);
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

        switch (messageType) {

            case HANDSHAKE -> {
                byte[] data = java.util.Base64.getDecoder().decode(rawPayload);
                return HandshakePayload.fromBytes(data);
            }

            case PEER_DISCOVERY_REQUEST -> {
                return null;
            }

            case PEER_DISCOVERY_RESPONSE -> {
                byte[] data = java.util.Base64.getDecoder().decode(rawPayload);
                return PeerResponsePayload.fromBytes(data);
            }

            case CIRCUIT_CREATE_REQUEST, CIRCUIT_CREATE_RESPONSE -> {
                byte[] data = java.util.Base64.getDecoder().decode(rawPayload);
                return CircuitCreatePayload.fromBytes(data);
            }

            case CIRCUIT_EXTEND_REQUEST, CIRCUIT_EXTEND_RESPONSE -> {
                byte[] data = java.util.Base64.getDecoder().decode(rawPayload);
                return CircuitExtendPayloadEncrypted.fromBytes(data);
            }

            case DATA_TRANSFER_REQUEST, DATA_TRANSFER_RESPONSE -> {
                byte[] data = java.util.Base64.getDecoder().decode(rawPayload);
                return CircuitDataPayload.fromBytes(data);
            }

            default -> throw new IllegalValueException("Unexpected MessageType: " + messageType);
        }
    }
}
