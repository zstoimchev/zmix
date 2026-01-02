package dev.network;

import dev.message.MessageBuilder;
import dev.models.PeerInfo;
import dev.models.enums.MessageType;
import dev.models.enums.PeerDirection;
import dev.protocol.CircuitProtocol;
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

    private final Crypto crypto;
    private final MessageQueue queue;
    private final String encodedPublicKey;

    private final ConcurrentHashMap<String, Peer> connectedPeers;
    private final List<PeerInfo> knownPeers;

    private final MessageHandler messageHandler;

    private final PeerDiscoveryProtocol peerDiscoveryProtocol;
    private final CircuitManager circuitManager;
    private final CircuitProtocol circuitProtocol;

    private final ScheduledExecutorService scheduler;


    public NetworkManager(Config config, MessageHandler messageHandler, MessageQueue queue, ExecutorService executor) {
        this.logger = Logger.getLogger(NetworkManager.class);
        this.nodeId = UUID.randomUUID();
        this.peerExecutor = executor;
        this.config = config;
        this.crypto = new Crypto();
        this.encodedPublicKey = Base64.getEncoder().encodeToString(crypto.getPublicKey().getEncoded());
        this.queue = queue;

        this.connectedPeers = new ConcurrentHashMap<>();
        this.knownPeers = new ArrayList<>();

        this.messageHandler = messageHandler;
        this.peerDiscoveryProtocol = new PeerDiscoveryProtocol(this);
        this.circuitManager = new CircuitManager(this);
        this.circuitProtocol = new CircuitProtocol(circuitManager, this);
        this.scheduler = Executors.newScheduledThreadPool(2);
        registerProtocols();
    }

    public void start() {
        logger.info("Starting network manager");
        isRunning.set(true);
        peerDiscoveryProtocol.init();
        scheduler.scheduleWithFixedDelay(
                this::startPeerMaintenance,
                config.getPeerDiscoveryInitialDelayInSeconds(),
                config.getPeerDiscoveryDelayInSeconds(),
                TimeUnit.SECONDS);
//        circuitManager.init();
    }

    public void registerPeer(Peer peer) {
        if (getKnownPeers().stream().noneMatch(p -> p.getPublicKey().equals(peer.getPublicKeyBase64Encoded())))
            addKnownPeer(new PeerInfo(peer.getPublicKeyBase64Encoded(), peer.getIp(), peer.getPort()));

        if (getConnectedPeerCount() >= config.getMaxConnections()) {
            logger.warn("Max peers reached. Cannot register new peer: {}", peer.getPeerId());
            Collections.shuffle(getKnownPeers());
            peer.send(MessageBuilder.buildPeerResponseMessage(getKnownPeers().stream().limit(5).toList()));
            peer.disconnect();
            return;
        }

        addConnectedPeer(peer);
        logger.info("Registered peer: {}", peer.getPeerId());
    }

    public void unregisterPeer(Peer peer) {
        removeConnectedPeer(peer);
        logger.info("Unregistered peer: {}", peer.getPeerId());
    }

    public void startPeerMaintenance() {
        if (getConnectedPeerCount() >= config.getMaxConnections()) return;

        List<PeerInfo> candidates = new ArrayList<>(knownPeers
                .stream()
                .filter(peer -> !connectedPeers
                        .containsKey(peer.getPublicKey()) && !peer.getPublicKey()
                        .equals(encodedPublicKey))
                .toList());

        Collections.shuffle(candidates);

        logger.debug("   >->   Connected: {}, Known: {}, Candidates: {}   <-<\n", getConnectedPeers().size(), getKnownPeers().size(), candidates.size());

        for (PeerInfo info : candidates) {
            if (connectedPeers.size() > config.getMinConnections()) break;
            logger.debug(" ................................., {}, {}", info.host, info.port);
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

    public synchronized int getConnectedPeerCount() {
        return connectedPeers.size();
    }

    public synchronized int getKnownPeerCount() {
        return knownPeers.size();
    }

    public synchronized void addConnectedPeer(Peer peer) {
        connectedPeers.put(peer.getPublicKeyBase64Encoded(), peer);
    }

    public synchronized void removeConnectedPeer(Peer peer) {
        connectedPeers.remove(peer.getPublicKeyBase64Encoded());
    }

    public synchronized void addKnownPeer(PeerInfo peerInfo) {
        knownPeers.add(peerInfo);
    }

    public synchronized void removeKnownPeer(PeerInfo peerInfo) {
        knownPeers.remove(peerInfo);
    }

    private void registerProtocols() {
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_REQUEST, peerDiscoveryProtocol);
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_RESPONSE, peerDiscoveryProtocol);
        messageHandler.registerProtocol(MessageType.CIRCUIT_CREATE_REQUEST, circuitProtocol);
        messageHandler.registerProtocol(MessageType.CIRCUIT_CREATE_RESPONSE, circuitProtocol);
        messageHandler.registerProtocol(MessageType.CIRCUIT_EXTEND_REQUEST, circuitProtocol);
        messageHandler.registerProtocol(MessageType.CIRCUIT_EXTEND_RESPONSE, circuitProtocol);
        logger.info("Registered all protocol handlers");
    }
}
