package dev;

import dev.interfaces.Gui;
import dev.models.MessageQueue;
import dev.network.NetworkManager;
import dev.network.Server;
import dev.interfaces.Cli;
import dev.protocol.MessageHandler;
import dev.utils.Config;
import dev.utils.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private final Logger logger;
    private final Server server;
    private final NetworkManager networkManager;
    private final MessageHandler messageHandler;
    private final Cli inputHandler;
    private final Gui gui;

    // DI and registering all the configuration
    public Main(String[] args) {
        this.logger = Logger.getLogger(Main.class);
        Config config = Config.load(args);
        MessageQueue queue = new MessageQueue();
        this.messageHandler = new MessageHandler(queue);
        ExecutorService executor = Executors.newCachedThreadPool();
        this.networkManager = new NetworkManager(config, messageHandler, queue, executor);
        this.server = new Server(config, queue, networkManager, executor);
        this.inputHandler = new Cli(networkManager);
        this.gui = new Gui(config.isGuiEnabled(args), networkManager);
    }

    public static void main(String[] args) {
        new Main(args).startNetwork();
    }

    private void startNetwork() {
        this.server.start();
        this.messageHandler.start();
        this.networkManager.start();
        this.inputHandler.start();
        this.gui.start();
    }
}