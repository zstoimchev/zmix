package dev.network;

import dev.message.MessageBuilder;
import dev.models.MessageQueue;
import dev.models.PeerInfo;
import dev.models.enums.MessageType;
import dev.models.enums.PeerDirection;
import dev.protocol.CircuitProtocol;
import dev.protocol.MessageHandler;
import dev.protocol.PeerDiscoveryProtocol;
import dev.utils.*;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class NetworkManager {
    private final Logger logger;
    private final UUID nodeId;

    private AtomicBoolean isRunning;
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
        this.encodedPublicKey = Utils.encodeBytesToString(crypto.getPublicKey().getEncoded());
        this.queue = queue;
        this.isRunning = new AtomicBoolean(false);

        this.connectedPeers = new ConcurrentHashMap<>();
        this.knownPeers = new ArrayList<>();

        this.messageHandler = messageHandler;
        this.peerDiscoveryProtocol = new PeerDiscoveryProtocol(this);
        this.circuitManager = new CircuitManager(this);
        this.circuitProtocol = new CircuitProtocol(circuitManager);
        this.scheduler = Executors.newScheduledThreadPool(2);
        registerProtocols();
    }

    public void start() {
        logger.info("Starting network manager");
        setReady();
        peerDiscoveryProtocol.init();
        scheduler.scheduleWithFixedDelay(
                this::startPeerMaintenance,
                config.getPeerDiscoveryInitialDelayInSeconds(),
                config.getPeerDiscoveryDelayInSeconds(),
                TimeUnit.SECONDS);
    }

    public synchronized void setReady() {
        isRunning.set(true);
        notifyAll();
    }

    public synchronized void waitUntilReady() throws InterruptedException {
        while (!isReady()) {
            wait();
        }
    }

    public synchronized boolean isReady() {
        return isRunning.get();
    }

    public void registerPeer(Peer peer) {
        if (getKnownPeers().stream().noneMatch(p -> p.getPublicKey().equals(peer.getPublicKeyEncoded())))
            addKnownPeer(new PeerInfo(peer.getPublicKeyEncoded(), peer.getIp(), peer.getPort()));

        if (getConnectedPeerCount() >= config.getMaxConnections()) {
            logger.warn("Max peers reached. Cannot register new peer: {}", peer.getPeerId());
            Collections.shuffle(getKnownPeers());
            peer.send(MessageBuilder.buildPeerResponseMessage(getKnownPeers().stream().limit(5).toList()));
            peer.disconnect();
            return;
        }

        addConnectedPeer(peer);
        logger.info("Registered peer: {}, {}:{}", peer.getPeerId(), peer.getIp(), peer.getPort());
    }

    public void unregisterPeer(Peer peer) {
        removeConnectedPeer(peer);
        circuitManager.destroyCircuitOnPeerDisconnect(peer);
        logger.info("Unregistered peer: {}", peer.getPeerId());
    }

    public void startPeerMaintenance() {
        logger.info("   >-->   Connected: {} | Known: {}   <--<", getConnectedPeers().size(), getKnownPeers().size());
        if (getConnectedPeerCount() >= config.getMaxConnections()) return;

        List<PeerInfo> candidates = new ArrayList<>(knownPeers
                .stream()
                .filter(peer -> !connectedPeers
                        .containsKey(peer.getPublicKey()) && !peer.getPublicKey()
                        .equals(encodedPublicKey))
                .toList());

        Collections.shuffle(candidates);

        for (PeerInfo info : candidates) {
            logger.info("Trying to connect to peer: {}:{}", info.host, info.port);
            if (connectedPeers.size() > config.getMaxConnections()) break;
            peerExecutor.submit(() -> connectToPeer(info.host, info.port));
        }
    }

    // GPT helper understand the exponential back-off, specifically how to define timeout interval
    public void connectToPeer(String ip, int port) {
        int attempts = config.getConnectionRetryAttempts();

        for (int retries = 1; retries < attempts; retries++) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), config.getConnectionTimeoutInMilliseconds());
                logger.info("Connected to node: {}", socket.getRemoteSocketAddress());
                peerExecutor.submit(new Peer(socket, queue, this, PeerDirection.OUTBOUND));
                return;
            } catch (IOException e) {
//                if (retries == attempts) break;
                long timeout = (1L << (retries - 1)) * 100;
                logger.warn("Failed connecting to {}:{} (attempt {}/{}). Retrying in {}ms...", ip, port, retries, attempts, timeout);
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException ex) {
                    logger.error("Interrupted while waiting for outbound node.", ex);
//                    Thread.currentThread().interrupt(); // figure out why
                    return;
                }
            }
        }
        logger.error("Could not connect to outbound node. Continuing alone");
    }

    public synchronized int getConnectedPeerCount() {
        return connectedPeers.size();
    }

    public synchronized int getKnownPeerCount() {
        return knownPeers.size();
    }

    public synchronized void addConnectedPeer(Peer peer) {
        connectedPeers.put(peer.getPublicKeyEncoded(), peer);
    }

    public synchronized void removeConnectedPeer(Peer peer) {
        connectedPeers.remove(peer.getPublicKeyEncoded());
    }

    public synchronized void addKnownPeer(PeerInfo peerInfo) {
        knownPeers.add(peerInfo);
    }

    public synchronized void removeKnownPeer(PeerInfo peerInfo) {
        knownPeers.remove(peerInfo);
    }

    public int getPort() {
        return config.getNodePort();
    }

    private void registerProtocols() {
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_REQUEST, peerDiscoveryProtocol);
        messageHandler.registerProtocol(MessageType.PEER_DISCOVERY_RESPONSE, peerDiscoveryProtocol);
        messageHandler.registerProtocol(MessageType.CIRCUIT_CREATE_REQUEST, circuitProtocol);
        messageHandler.registerProtocol(MessageType.CIRCUIT_CREATE_RESPONSE, circuitProtocol);
        messageHandler.registerProtocol(MessageType.CIRCUIT_EXTEND_REQUEST, circuitProtocol);
        messageHandler.registerProtocol(MessageType.CIRCUIT_EXTEND_RESPONSE, circuitProtocol);
        messageHandler.registerProtocol(MessageType.DATA_TRANSFER_REQUEST, circuitProtocol);
        messageHandler.registerProtocol(MessageType.DATA_TRANSFER_RESPONSE, circuitProtocol);
        logger.info("Registered all protocol handlers");
    }
}
