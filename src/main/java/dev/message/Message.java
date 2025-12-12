package dev.message;

import dev.message.payload.MessagePayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    private MessageType type;
    private long timestamp;

    private String messageId;
    private String signature;

    private MessagePayload payload;

    @Override
    public String toString() {
        return "Message{type=" + type + ", id=" + messageId + ", senderPublicKey=" + ", payload=" + payload + "}";
    }
}

