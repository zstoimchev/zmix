package dev.models;

import dev.models.enums.MessageType;
import dev.message.payload.MessagePayload;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Message {
    private MessageType messageType;
    private long timestamp;
    private String messageId;
    private MessagePayload payload;

    public Message(MessageType messageType, long timestamp, String messageId, MessagePayload payload) {
        this.messageType = messageType;
        this.timestamp = timestamp;
        this.messageId = messageId;
        this.payload = payload;
    }
}