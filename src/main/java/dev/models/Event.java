package dev.models;

import dev.network.Peer;

public record Event(Peer sender, Message message) {}
