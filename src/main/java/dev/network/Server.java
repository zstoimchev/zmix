package dev.network;

import dev.network.peer.Peer;
import dev.network.peer.PeerDirection;
import dev.utils.Config;
import dev.utils.CustomException;
import dev.utils.Logger;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server extends Thread {
    private final Logger logger;
    private final ExecutorService peerExecutor;
    private final Config config;
    private final MessageQueue queue;
    private final NetworkManager networkManager;

    public Server(Config config, MessageQueue queue, NetworkManager networkManager) {
        this.setName("Server");

        this.logger = Logger.getLogger(this.getClass());
        this.config = config;
        this.peerExecutor = Executors.newCachedThreadPool();
        this.queue = queue;
        this.networkManager = networkManager;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(config.getNodePort())) {
            logger.info("Server started and waiting for connections on port " + config.getNodePort());
logger.debug("S E R V E R O N P O R T 111111111111111111111");
            if (!config.isBootstrapNode()) connectToBootstrapNodes();
            logger.debug("S E R V E R O N P O R T 222222222222222222222");

            while (!this.isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                logger.info("---- New connection: ----");
                logger.info(" -> Local IP:    " + clientSocket.getLocalAddress().getHostAddress());
                logger.info(" -> Local Port:  " + clientSocket.getLocalPort());
                logger.info(" -> Remote IP:   " + clientSocket.getInetAddress().getHostAddress());
                logger.info(" -> Remote Port: " + clientSocket.getPort());
                logger.info("-------------------------");
                peerExecutor.submit(new Peer(clientSocket, queue, networkManager, PeerDirection.INBOUND));
                logger.debug("S E R V E R O N P O R T 333333333333333333");
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
            peerExecutor.submit(new Peer(socket, queue, networkManager, PeerDirection.OUTBOUND));
        } catch (IOException e) {
            logger.error("Could not connect to Bootstrap Node. Continuing on my own...", e);
        }
    }

    public void shutdown() {
        this.interrupt();
        peerExecutor.shutdownNow();
        logger.info("Server stopped.");
    }
}
