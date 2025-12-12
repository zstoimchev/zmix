package dev.network;

import dev.message.Message;
import dev.message.MessageBuilder;
import dev.message.MessageType;
import dev.protocol.MessageHandler;
import dev.protocol.PeerDiscoveryProtocol;
import dev.utils.Config;
import dev.utils.Crypto;
import dev.utils.Logger;
import lombok.Getter;
import lombok.Setter;

import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
public class NetworkManager {
    private final Logger logger;
    private final UUID nodeId;

    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private final Config config;
    private ConcurrentHashMap<UUID, Peer> connectedPeers;
    private ConcurrentHashMap<String, Peer> pendingPeers;
    
    private final MessageBuilder messageBuilder;
    private final Crypto crypto;

    private final MessageHandler messageHandler;

    private final PeerDiscoveryProtocol peerDiscoveryProtocol;

    public NetworkManager(Config config, MessageHandler messageHandler) {
        this.logger = Logger.getLogger(NetworkManager.class);
        this.nodeId = UUID.randomUUID();
        this.config = config;
        this.connectedPeers = new ConcurrentHashMap<>();
        this.pendingPeers = new ConcurrentHashMap<>();
        this.crypto = new Crypto();
        messageBuilder = new MessageBuilder();

        this.messageHandler = messageHandler;
        this.peerDiscoveryProtocol = new PeerDiscoveryProtocol(this);
        registerProtocols();
    }

    public void start() {
        logger.info("Starting network manager");
        isRunning.set(true);
    }

    public void registerPeer(Peer peer) {
        UUID peerId = peer.getPeerId();
        connectedPeers.put(peerId, peer);
        logger.info("Registered peer: {}", peerId);
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
        logger.debug("Broadcasting message type {} to {} peers", message.getType(), connectedPeers.size());

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
}

