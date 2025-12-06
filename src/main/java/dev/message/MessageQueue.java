package dev.message;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MessageQueue {
    public BlockingQueue<Event> queue = new LinkedBlockingQueue<>();
}
