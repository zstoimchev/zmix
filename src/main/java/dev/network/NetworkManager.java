package dev.network;

import dev.message.enums.MessageType;
import dev.network.peer.Peer;
import dev.network.peer.PeerDirection;
import dev.protocol.MessageHandler;
import dev.protocol.PeerDiscoveryProtocol;
import dev.utils.Config;
import dev.utils.Crypto;
import dev.utils.CustomException;
import dev.utils.Logger;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class NetworkManager {
    private final Logger logger;
    private final UUID nodeId;

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ExecutorService peerExecutor;
    private final Config config;
    private final ConnectionManager connectionManager;

    private final Crypto crypto;
    private final MessageQueue queue;
    private final String encodedPublicKey;

    private final MessageHandler messageHandler;

    private final PeerDiscoveryProtocol peerDiscoveryProtocol;

    private final ScheduledExecutorService scheduler;


    public NetworkManager(Config config, MessageHandler messageHandler, MessageQueue queue, ExecutorService executor) {
        this.logger = Logger.getLogger(NetworkManager.class);
        this.nodeId = UUID.randomUUID();
        this.peerExecutor = executor;
        this.config = config;
        this.connectionManager = new ConnectionManager(this);
        this.crypto = new Crypto();
        this.encodedPublicKey = Base64.getEncoder().encodeToString(crypto.getPublicKey().getEncoded());
        this.queue = queue;

        this.messageHandler = messageHandler;
        this.peerDiscoveryProtocol = new PeerDiscoveryProtocol(this);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        registerProtocols();
    }

    public void start() {
        logger.info("Starting network manager");
        isRunning.set(true);
        peerDiscoveryProtocol.init();
        connectionManager.init();
    }

    public void registerPeer(Peer peer) {
        connectionManager.registerPeer(peer);
    }

    public void unregisterPeer(Peer peer) {
        connectionManager.unregisterPeer(peer);
    }

    public void connectToPeer(String ip, int port) {
        try {
            Socket clientSocket = new Socket(ip, port);
            Peer newPeer = new Peer(clientSocket, queue, this, PeerDirection.OUTBOUND);
            peerExecutor.submit(newPeer);
        } catch (IOException e) {
            throw new CustomException("Failed connecting to new peer", e);
        }
    }

    // Register protocols in Message Handler
    private void registerProtocols() {
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_REQUEST, peerDiscoveryProtocol);
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_RESPONSE, peerDiscoveryProtocol);
        logger.info("Registered all protocol handlers");
    }
}
