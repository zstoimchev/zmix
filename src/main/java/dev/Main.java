package dev;

import dev.message.MessageQueue;
import dev.network.NetworkManager;
import dev.network.Server;
import dev.protocol.MessageHandler;
import dev.utils.Config;
import dev.utils.CustomException;
import dev.utils.Logger;

public class Main {
    private final Logger logger;
    private final Config config;
    private final Server server;

    // DI and registering all the configuration
    public Main(String[] args) {
        this.logger = Logger.getLogger(Main.class);
        this.config = Config.load(args[0]);
        MessageQueue queue = new MessageQueue();
        MessageHandler messageHandler = new MessageHandler(queue);
        NetworkManager networkManager = new NetworkManager(config, messageHandler);
        this.server = new Server(config, queue, networkManager);
    }

    public static void main(String[] args) {
        // TODO: validate args & add a shutdown hook to gracefully stop the network
        if (args.length < 1) throw new CustomException("Please specify the node configuration!", null);
        new Main(args).startNetwork();
    }

    private void startNetwork() {
        logger.info("Starting network on port: {}...", config.getNodePort());
        this.server.start();
    }
}