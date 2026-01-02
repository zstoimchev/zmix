package dev.network;

import dev.models.Message;

public record Event(Peer sender, Message message) {}
