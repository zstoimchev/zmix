package dev;

import dev.network.MessageQueue;
import dev.network.NetworkManager;
import dev.network.Server;
import dev.protocol.MessageHandler;
import dev.utils.Config;
import dev.utils.CustomException;
import dev.utils.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private final Logger logger;
    private final Config config;
    private final Server server;
    private final ExecutorService executor;
    private final NetworkManager networkManager;
    private final MessageHandler messageHandler;

    // DI and registering all the configuration
    public Main(String[] args) {
        this.logger = Logger.getLogger(Main.class);
        this.config = Config.load(args[0]);
        MessageQueue queue = new MessageQueue();
        this.messageHandler = new MessageHandler(queue);
        this.executor = Executors.newCachedThreadPool();
        this.networkManager = new NetworkManager(config, messageHandler, queue, executor);
        this.server = new Server(config, queue, networkManager, executor);
    }

    public static void main(String[] args) {
        // TODO: validate args & add a shutdown hook to gracefully stop the network
        if (args.length < 1) throw new CustomException("Please specify the node configuration!", null);
        new Main(args).startNetwork();
    }

    private void startNetwork() {
        logger.info("Starting network on port: {}...", config.getNodePort());
        this.server.start();
        this.messageHandler.start();
        this.networkManager.start();
    }
}