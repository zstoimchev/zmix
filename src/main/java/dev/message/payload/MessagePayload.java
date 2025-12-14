package dev.message.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "payloadType")
@JsonSubTypes({@JsonSubTypes.Type(value = HandshakePayload.class, name = "HANDSHAKE"),
        @JsonSubTypes.Type(value = PeerResponsePayload.class, name = "PEER_DISCOVERY_RESPONSE")})
public abstract class MessagePayload {
}
