package dev.network;

import lombok.Getter;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Getter
public class MessageQueue {
    private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>();
}
