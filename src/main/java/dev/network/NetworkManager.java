package dev.network;

import dev.message.Message;
import dev.message.MessageBuilder;
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
    private final ConcurrentHashMap<String, Peer> connectedPeers;
    private final ConcurrentHashMap<String, Peer> pendingPeers;
    private final List<PeerInfo> knownPeers;
    
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
        this.knownPeers = new ArrayList<>();
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
                this::startPeerMaintenance,
                config.getPeerDiscoveryInitialDelayInSeconds(),
                config.getPeerDiscoveryDelayInSeconds(),
                TimeUnit.SECONDS
        );
    }

    public void registerPeer(Peer peer) {
        if (connectedPeers.size() >= config.getMaxConnections()) {
            logger.warn("Max peers reached. Cannot register new peer: {}", peer.getPeerId());
            return;
        }
        connectedPeers.put(peer.getPublicKeyBase64Encoded(), peer);
        knownPeers.add(new PeerInfo(
                peer.getPublicKeyBase64Encoded(),
                peer.getIp(),
                peer.getPort()
        ));
        logger.info("Registered peer: {}", peer.getPeerId());
        logger.debug(" -------------------------------------> Connected peers: {}", getConnectedPeers().size());
        logger.debug(" -------------------------------------> Known peers: {}", getKnownPeers().size());
    }

    public void unregisterPeer(Peer peer) {
        connectedPeers.remove(peer.getPublicKeyBase64Encoded());
        logger.info("Unregistered peer: {}", peer.getPeerId());
    }

    public PublicKey getPublicKey() {
        return crypto.getPublicKey();
    }

    public String getEncodedPublicKey() {
        return Base64.getEncoder().encodeToString(getPublicKey().getEncoded());
    }

    // Register protocols in Message Handler
    private void registerProtocols() {
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_REQUEST, peerDiscoveryProtocol);
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_RESPONSE, peerDiscoveryProtocol);
        logger.info("Registered all protocol handlers");
    }

    public void startPeerMaintenance() {
        logger.debug("A T T E M P T I N G   T O   C O N N E C T   T O   N E W   P E E R S . . . ");
        if (!isRunning.get()) return;

        if (connectedPeers.size() >= config.getMaxConnections()) {
            return;
        }

//        List<PeerInfo> candidates = new ArrayList<>(getKnownPeers());
        List<PeerInfo> candidates = new ArrayList<>(knownPeers.stream()
                .filter(peer -> !connectedPeers.containsKey(peer.getPublicKey()))
                .filter(peerInfo -> !peerInfo.getPublicKey().equals(getEncodedPublicKey()))
                .toList());

        Collections.shuffle(candidates);

        logger.debug(" - - - - - - - - - - - - - - - - - - - - - - - - - - - Connected peers: " + getConnectedPeers().size());
        logger.debug(" - - - - - - - - - - - - - - - - - - - - - - - - - - - Known peers: " + getKnownPeers().size());
        logger.debug(" - - - - - - - - - - - - - - - - - - - - - - - - - - - Candidate peers: " + candidates.size());

        for (PeerInfo info : candidates) {
            logger.debug(" - - - - - - - - - connecting - - - - - - - - - - - - - - - - - - " + candidates.size());

            if (connectedPeers.size() > config.getMaxConnections()) break;
            if (isAlreadyConnected(info)) continue;
            connectToPeer(info.host, info.port);
        }
    }

    private boolean isAlreadyConnected(PeerInfo info) {
        return connectedPeers.containsKey(info.publicKey);
    }

    private void initializePeerDiscovery() {
        Message peerRequest = MessageBuilder.buildPeerRequestMessage();
        broadcast(peerRequest);
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

    public void connectToPeer(String ip, int port) {
        try {
            Socket clientSocket = new Socket(ip, port);
            Peer newPeer = new Peer(clientSocket, queue, this, PeerDirection.OUTBOUND);
            peerExecutor.submit(newPeer);
        } catch (IOException e) {
            throw new CustomException("Failed connecting to new peer", e);
        }
    }

    public void addKnownPeer(PeerInfo peerInfo) {
        this.knownPeers.add(peerInfo);
    }
}
