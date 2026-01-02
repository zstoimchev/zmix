package dev.message.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircuitCreatePayload extends MessagePayload{
    public final UUID circuitId;
    public final String ephemeralKey;
}
