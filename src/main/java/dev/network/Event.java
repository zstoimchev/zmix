package dev.network;

import dev.message.Message;
import dev.network.peer.Peer;

public record Event(Peer sender, Message message) {}
