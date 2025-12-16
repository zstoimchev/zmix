package dev.protocol;

import dev.message.Message;
import dev.message.MessageBuilder;
import dev.message.payload.PeerResponsePayload;
import dev.network.NetworkManager;
import dev.network.peer.Peer;
import dev.network.peer.PeerInfo;
import dev.utils.Config;
import dev.utils.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class PeerDiscoveryProtocol implements Protocol {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final Map<String, PeerInfo> knownPeers;
    private final Config config;

    public PeerDiscoveryProtocol(NetworkManager networkManager, Config config) {
        this.logger = Logger.getLogger(this.getClass());
        this.networkManager = networkManager;
        this.knownPeers = new HashMap<>();
        this.config = config;
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

        List<PeerInfo> peerList = networkManager.getConnectedPeers().values().stream()
                .filter(p -> !p.getPeerId().equals(peer.getPeerId()))
                .map(p -> new PeerInfo(
                        p.getPublicKeyBase64Encoded(),
                        p.getIp(),
                        p.getPort()
                ))
                .limit(20)
                .collect(Collectors.toList());

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
                if (!knownPeers.containsKey(publicKey)) {
                    knownPeers.put(publicKey, peerInfo);
                    newPeers++;
                }
            }
        }

        logger.info("Discovered {} new peers (total known: {})", newPeers, knownPeers.size());
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

    public void initPeerDiscovery() {
        logger.info("Initializing peer discovery");
        // broadcastPeerRequest();

//        Collections.shuffle((List<?>) networkManager.getPendingPeers());

        ArrayList<Peer> peers = new ArrayList<>(networkManager.getPendingPeers().values());
        Collections.shuffle(peers);

        while(networkManager.getConnectedPeers().size() <= config.getMaxConnections()) {
            for (Peer peer : networkManager.getPendingPeers().values()) {
                // create a connection to the peer
                networkManager.connectToPeer(peer.getIp(), peer.getPort());
            }
        }
    }
}
