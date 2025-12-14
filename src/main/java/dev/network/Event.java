package dev.network;

import dev.message.Message;
import dev.network.peer.Peer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Event {
    public final Peer sender;
    public final Message message;
}
