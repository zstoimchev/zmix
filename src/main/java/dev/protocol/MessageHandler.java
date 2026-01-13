package dev.protocol;

import dev.models.Event;
import dev.models.Message;
import dev.network.MessageQueue;
import dev.models.enums.MessageType;
import dev.network.Peer;
import dev.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageHandler extends Thread implements Protocol {
    private final Logger logger;
    private final MessageQueue messageQueue;
    private final Map<MessageType, Protocol> protocolHandlers;
    private final ArrayList<String> history = new ArrayList<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public MessageHandler(MessageQueue messageQueue) {
        this.setName("MessageHandler");
        this.logger = Logger.getLogger(this.getClass());
        this.messageQueue = messageQueue;
        this.protocolHandlers = new HashMap<>();
    }

    public void registerProtocol(MessageType messageType, Protocol protocol) {
        protocolHandlers.put(messageType, protocol);
        logger.info("Registered protocol handler for: {}", messageType);
    }

    @Override
    public void run() {
        isRunning.set(true);
        logger.info("MessageProcessor started");

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Event event = messageQueue.getQueue().take();
                if (history.contains(event.message().getMessageId())) {
                    continue;
                }

                history.add(event.message().getMessageId());
                digest(event.sender(), event.message());
            } catch (InterruptedException e) {
                logger.info("MessageProcessor interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing message", e);
            }
        }

        logger.info("MessageProcessor stopped");
    }

    @Override
    public void digest(Peer peer, Message message) {
        logger.debug("Processing message type: {} from peer: {}", message.getMessageType(), peer.getPeerId());

        Protocol handler = protocolHandlers.get(message.getMessageType());

        if (handler == null) {
            logger.warn("No protocol handler registered for message type: {}", message.getMessageType());
            peer.disconnect();
            return;
        }

        try {
            handler.digest(peer, message);
        } catch (Exception e) {
            logger.error("Error in protocol handler for {}: {}", message.getMessageType(), e.getMessage(), e);
        }
    }

    public void shutdown() {
        logger.info("Shutting down MessageProcessor");
        isRunning.set(false);
        this.interrupt();
    }
}
