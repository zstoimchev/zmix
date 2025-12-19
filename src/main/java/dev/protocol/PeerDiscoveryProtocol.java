package dev.protocol;

import dev.message.Message;
import dev.message.MessageBuilder;
import dev.message.payload.PeerResponsePayload;
import dev.network.NetworkManager;
import dev.network.peer.Peer;
import dev.network.peer.PeerInfo;
import dev.utils.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PeerDiscoveryProtocol implements Protocol {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final Map<String, PeerInfo> knownPeers;

    public PeerDiscoveryProtocol(NetworkManager networkManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.networkManager = networkManager;
        this.knownPeers = new ConcurrentHashMap<>();
    }

    public Collection<PeerInfo> getKnownPeers() {
        return knownPeers.values();
    }

    @Override
    public void digest(Peer peer, Message message) {
        switch (message.getMessageType()) {
            case PEER_DISCOVERY_REQUEST:
                handlePeerDiscoveryRequest(peer, message);
                break;
            case PEER_DISCOVERY_RESPONSE:
                handlePeerDiscoveryResponse(peer, message);
                break;
            default:
                logger.warn("PeerDiscoveryProtocol received unexpected message type: {}", message.getMessageType());
        }
    }

    private void handlePeerDiscoveryRequest(Peer peer, Message message) {
        logger.info("Received peer request from: {}", peer.getPeerId());

        List<PeerInfo> peerList = networkManager.getKnownPeers();
        Message response = MessageBuilder.buildPeerResponseMessage(peerList);
        peer.send(response);
        logger.info("Sent {} peers to: {}", peerList.size(), peer.getPeerId());
    }

    private void handlePeerDiscoveryResponse(Peer peer, Message message) {
        logger.info("Received peer response from: {}", peer.getPeerId());
        PeerResponsePayload payload = (PeerResponsePayload) message.getPayload();
        if (payload == null || payload.getPeerList() == null) {
            logger.warn("Received empty peer response");
            return;
        }

        int newPeers = 0;
        for (PeerInfo peerInfo : payload.getPeerList()) {
            String publicKey = peerInfo.publicKey;
            String host = peerInfo.host;
            Integer port = peerInfo.port;

            if (publicKey != null && host != null && port != null) {
                PeerInfo newPeerInfo = new PeerInfo(publicKey, host, port);
                if (isKnown(newPeerInfo) || isSelf(newPeerInfo.getPublicKey())) continue;
                networkManager.addKnownPeer(newPeerInfo);
                newPeers++;
            }
        }

        logger.info("Discovered {} new peers (total known: {})", newPeers, networkManager.getKnownPeers().size());
    }

    private boolean isKnown(PeerInfo peerInfo) {
        boolean presentInKnown = knownPeers.values().stream().anyMatch(p -> p.getPublicKey().equals(peerInfo.getPublicKey()));
        boolean presentInConnected = networkManager.getConnectedPeers().values().stream().anyMatch(p -> p.getPublicKeyBase64Encoded().equals(peerInfo.getPublicKey()));
        return presentInKnown || presentInConnected;
    }

    private boolean isSelf(String publicKey) {
        return networkManager.getEncodedPublicKey().equals(publicKey);
    }

    public void requestPeers(Peer peer) {
        logger.info("Requesting peers from peer: {}", peer.getPeerId());
        Message request = MessageBuilder.buildPeerRequestMessage();
        peer.send(request);
    }

    public void broadcastPeerRequest() {
        logger.info("Broadcasting peer request to all connected peers");
        Message request = MessageBuilder.buildPeerRequestMessage();

        for (Peer peer : networkManager.getConnectedPeers().values()) {
            peer.send(request);
        }
    }
}
