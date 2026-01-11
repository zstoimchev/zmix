package dev;

import dev.network.MessageQueue;
import dev.network.NetworkManager;
import dev.network.Server;
import dev.protocol.InputHandler;
import dev.protocol.MessageHandler;
import dev.utils.Config;
import dev.utils.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private final Logger logger;
    private final Config config;
    private final Server server;
    private final NetworkManager networkManager;
    private final MessageHandler messageHandler;
    private final InputHandler inputHandler;

    // DI and registering all the configuration
    public Main(String arg) {
        this.logger = Logger.getLogger(Main.class);
        this.config = Config.load(arg);
        MessageQueue queue = new MessageQueue();
        this.messageHandler = new MessageHandler(queue);
        ExecutorService executor = Executors.newCachedThreadPool();
        this.networkManager = new NetworkManager(config, messageHandler, queue, executor);
        this.server = new Server(config, queue, networkManager, executor);
        this.inputHandler = new InputHandler(networkManager.getCircuitManager());
    }

    public static void main(String[] args) {
        new Main(args.length == 1 ? args[0] : "node.peer.properties").startNetwork();
    }

    private void startNetwork() {
        logger.info("Starting network on port: {}...", config.getNodePort());
        this.server.start();
        this.messageHandler.start();
        this.networkManager.start();
        this.inputHandler.start();
    }
}