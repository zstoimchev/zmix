package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CircuitExtendPayload extends MessagePayload {
    private final String publicKey;
    private final String host;
    private final Integer port;
    private final String sharedKey;
}
