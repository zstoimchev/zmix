package dev.network;

import dev.message.Message;
import dev.message.enums.MessageType;
import dev.network.peer.Peer;
import dev.network.peer.PeerDirection;
import dev.network.peer.PeerInfo;
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
import java.security.PublicKey;
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
    private final ConcurrentHashMap<UUID, Peer> connectedPeers;
    private final ConcurrentHashMap<String, Peer> pendingPeers;
    private final Map<String, PeerInfo> knownPeers;
    
    private final Crypto crypto;
    private final MessageQueue queue;

    private final MessageHandler messageHandler;

    private final PeerDiscoveryProtocol peerDiscoveryProtocol;

    private final ScheduledExecutorService scheduler;


    public NetworkManager(Config config, MessageHandler messageHandler, MessageQueue queue) {
        this.logger = Logger.getLogger(NetworkManager.class);
        this.nodeId = UUID.randomUUID();
        this.peerExecutor = Executors.newCachedThreadPool();
        this.config = config;
        this.connectedPeers = new ConcurrentHashMap<>();
        this.pendingPeers = new ConcurrentHashMap<>();
        this.knownPeers = new ConcurrentHashMap<>();
        this.crypto = new Crypto();
        this.queue = queue;

        this.messageHandler = messageHandler;
        this.peerDiscoveryProtocol = new PeerDiscoveryProtocol(this);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        registerProtocols();
    }

    public void start() {
        logger.info("Starting network manager");
        isRunning.set(true);

        scheduler.scheduleWithFixedDelay(
                this::runPeerDiscovery,
                5,
//                config.getPeerDiscoveryIntervalSeconds(),
                5,
                TimeUnit.SECONDS
        );
    }

    public void registerPeer(Peer peer) {
        if (connectedPeers.size() >= config.getMaxConnections()) {
            logger.warn("Max peers reached. Cannot register new peer: {}", peer.getPeerId());
            return;
        }
        connectedPeers.put(peer.getPeerId(), peer);
        knownPeers.put(peer.getPeerId().toString(), new PeerInfo(
                peer.getPublicKeyBase64Encoded(),
                peer.getIp(),
                peer.getPort()
        ));
        logger.info("Registered peer: {}", peer.getPeerId());
    }

    public void unregisterPeer(Peer peer) {
        connectedPeers.remove(peer.getPeerId());
        logger.info("Unregistered peer: {}", peer.getPeerId());
    }

    public PublicKey getPublicKey() {
        return crypto.getPublicKey();
    }

    public String getEncodedPublicKey() {
        return Base64.getEncoder().encodeToString(getPublicKey().getEncoded());
    }

    public void broadcast(Message message) {
        logger.debug("Broadcasting message type {} to {} peers", message.getMessageType(), connectedPeers.size());

        for (Peer peer : connectedPeers.values()) {
            try {
                peer.send(message);
            } catch (Exception e) {
                logger.error("Failed to send message to peer: {}", peer.getPeerId(), e);
            }
        }
    }

    // Register protocols in Message Handler
    private void registerProtocols() {
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_REQUEST, peerDiscoveryProtocol);
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_RESPONSE, peerDiscoveryProtocol);
        logger.info("Registered all protocol handlers");
    }

    public void runPeerDiscovery() {
        logger.debug("R U N N I N G   P E E R   D I S C O V E R Y . . . ");
        if (!isRunning.get()) return;

        if (connectedPeers.size() >= config.getMaxConnections()) {
            return;
        }

        List<PeerInfo> candidates = new ArrayList<>(peerDiscoveryProtocol.getKnownPeers());

        Collections.shuffle(candidates);

        for (PeerInfo info : candidates) {
            if (connectedPeers.size() >= config.getMaxConnections()) break;
            connectToPeer(info.host, info.port);
        }
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
}
