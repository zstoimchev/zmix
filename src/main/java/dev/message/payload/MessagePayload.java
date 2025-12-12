package dev.message.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "payloadType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = HandshakePayload.class, name = "HANDSHAKE"),
        @JsonSubTypes.Type(value = PeerRequestPayload.class, name = "PEER_REQUEST")
})
public abstract class MessagePayload {
}
