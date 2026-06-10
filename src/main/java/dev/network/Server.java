package dev.network;

import dev.models.MessageQueue;
import dev.models.enums.PeerDirection;
import dev.utils.Config;
import dev.utils.Logger;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

public class Server extends Thread {
    private final Logger logger;
    private final ExecutorService peerExecutor;
    private final Config config;
    private final MessageQueue queue;
    private final NetworkManager networkManager;

    public Server(Config config, MessageQueue queue, NetworkManager networkManager, ExecutorService peerExecutor) {
        this.setName("Server");

        this.logger = Logger.getLogger(this.getClass());
        this.config = config;
        this.peerExecutor = peerExecutor;
        this.queue = queue;
        this.networkManager = networkManager;
    }

    @Override
    public void run() {
        int backlog = config.getMaxConnections();
        try (ServerSocket serverSocket = new ServerSocket(config.getNodePort(), backlog)) {
            logger.info("Server started and waiting for connections on port " + config.getNodePort());
            if (!config.isBootstrapNode()) peerExecutor.submit(this::connectToBootstrapNodes);

            while (!this.isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                logger.info("""
                        =========== New connection: ===========
                         -> Remote IP: {}:{}
                        =======================================
                        """,  clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());
                peerExecutor.submit(new Peer(clientSocket, queue, networkManager, PeerDirection.INBOUND));
            }
        } catch (BindException e) {
            logger.error("Port " + config.getNodePort() + " is already in use.", e);
            throw new RuntimeException("Port " + config.getNodePort() + " is already in use.", e);
        } catch (IOException e) {
            logger.error("Could not start the server.", e);
            throw new  RuntimeException("Could not start the server.", e);
        } finally {
            shutdown();
        }
    }

    private void connectToBootstrapNodes() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(0, 1000 * 30));
        } catch (InterruptedException e) {
            logger.alert(e, "Connection initialization cooldown timeout interrupted...");
        }

        logger.info("Connecting to the bootstrap node at {}:{}", config.getBootstrapNodeHost(), config.getBootstrapNodePort());
        networkManager.connectToPeer(config.getBootstrapNodeHost(), config.getBootstrapNodePort());
    }

    public void shutdown() {
        this.interrupt();
        peerExecutor.shutdownNow();
        logger.info("Server stopped.");
    }
}
