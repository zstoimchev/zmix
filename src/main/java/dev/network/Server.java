package dev.network;

import dev.models.enums.PeerDirection;
import dev.utils.Config;
import dev.utils.CustomException;
import dev.utils.Logger;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {
    private final Logger logger;
    private final Config config;
    private final MessageQueue queue;
    private final NetworkManager networkManager;

    public Server(Config config, MessageQueue queue, NetworkManager networkManager) {
        this.setName("Server");

        this.logger = Logger.getLogger(this.getClass());
        this.config = config;
        this.queue = queue;
        this.networkManager = networkManager;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(config.getNodePort())) {
            logger.info("Server started and waiting for connections on port " + config.getNodePort());
            if (!config.isBootstrapNode()) connectToBootstrapNodes();

            while (!this.isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                logger.info("======= New connection: =======");
                logger.info("  -> Remote IP:   " + clientSocket.getInetAddress().getHostAddress());
                logger.info("  -> Remote Port: " + clientSocket.getPort());
                logger.info("===============================");
                networkManager.submitPeer(new Peer(clientSocket, queue, networkManager, PeerDirection.INBOUND));
            }
        } catch (BindException e) {
            logger.error("Port " + config.getNodePort() + " is already in use.", e);
            throw new CustomException("Port already in use: " + config.getNodePort(), e);
        } catch (IOException e) {
            logger.error("Could not start the server.", e);
            throw new CustomException("Could not start the server.", e);
        } finally {
            shutdown();
        }
    }

    private void connectToBootstrapNodes() {
        try {
            Socket socket = new Socket(config.getBootstrapNodeHost(), config.getBootstrapNodePort());
            logger.info("Connected to bootstrap node: " + socket.getRemoteSocketAddress());
            networkManager.submitPeer(new Peer(socket, queue, networkManager, PeerDirection.OUTBOUND));
        } catch (IOException e) {
            logger.error("Could not connect to Bootstrap Node. Continuing on my own...", e);
        }
    }

    public void shutdown() {
        this.interrupt();
        networkManager.getPeerExecutor().shutdown();
        logger.info("Server stopped.");
    }
}
