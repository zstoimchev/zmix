package dev.network;

import dev.message.MessageBuilder;
import dev.network.peer.Peer;
import dev.network.peer.PeerInfo;
import dev.utils.Config;
import dev.utils.Logger;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
public class ConnectionManager {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, Peer> connectedPeers;
    private final List<PeerInfo> knownPeers;
    private final Config config;
    private final String publicKeyBase64Encoded;

    public ConnectionManager(NetworkManager networkManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.networkManager = networkManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.connectedPeers = new ConcurrentHashMap<>();
        this.knownPeers = new ArrayList<>();
        this.config = networkManager.getConfig();
        this.publicKeyBase64Encoded = networkManager.getEncodedPublicKey();
    }

    public void init() {
        scheduler.scheduleWithFixedDelay(
                this::startPeerMaintenance,
                config.getPeerDiscoveryInitialDelayInSeconds(),
                config.getPeerDiscoveryDelayInSeconds(),
                TimeUnit.SECONDS);
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

        List<PeerInfo> candidates = new ArrayList<>(knownPeers.stream().filter(peer -> !connectedPeers.containsKey(peer.getPublicKey()) && !peer.getPublicKey().equals(publicKeyBase64Encoded)).toList());

        Collections.shuffle(candidates);

        logger.debug("   >->   Connected: {}, Known: {}, Candidates: {}   <-<\n", getConnectedPeers().size(), getKnownPeers().size(), candidates.size());

        for (PeerInfo info : candidates) {
            if (connectedPeers.size() > config.getMinConnections()) break;
            logger.debug(" ................................., {}, {}", info.host, info.port);
            networkManager.connectToPeer(info.host, info.port);
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
}
