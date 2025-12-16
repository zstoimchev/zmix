package dev.message;

import dev.message.enums.MessageType;
import dev.message.enums.PayloadType;
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
    private MessageType messageType;
    private PayloadType payloadType;
    private long timestamp;

    private String messageId;
    private String signature;

    private MessagePayload payload;
}