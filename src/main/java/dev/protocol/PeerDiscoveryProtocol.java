package dev.protocol;

import dev.message.Message;
import dev.message.payload.PeerRequestPayload;
import dev.network.NetworkManager;
import dev.network.Peer;
import dev.network.PeerInfo;
import dev.utils.Logger;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PeerDiscoveryProtocol implements Protocol {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final Map<String, PeerInfo> knownPeers;

    public PeerDiscoveryProtocol(NetworkManager networkManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.networkManager = networkManager;
        this.knownPeers = new HashMap<>();
    }

    @Override
    public void digest(Peer peer, Message message) {
        switch (message.getType()) {
            case PEER_DISCOVERY_REQUEST:
                handlePeerDiscoveryRequest(peer, message);
                break;
            case PEER_DISCOVERY_RESPONSE:
                handlePeerDiscoveryResponse(peer, message);
                break;
            case CIRCUIT_CREATE_REQUEST:
                // call handleCircuitCreateRequest(peer, message);
                break;
            case CREATE_CIRCUIT_RESPONSE:
                // call handleCircuitCreateResponse(peer, message);
                break;
            default:
                logger.warn("PeerDiscoveryProtocol received unexpected message type: {}", message.getType());
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

        Message response = networkManager.getMessageBuilder().buildPeerResponseMessage(peerList);
        peer.send(response);
        logger.info("Sent {} peers to: {}", peerList.size(), peer.getPeerId());
    }

    private void handlePeerDiscoveryResponse(Peer peer, Message message) {
        logger.info("Received peer response from: {}", peer.getPeerId());

        PeerRequestPayload payload = (PeerRequestPayload) message.getPayload();
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
        // TODO: attempt to connect to some of the new peers
        // or maybe schedule connection attempts later ? ? ?
        // connectToNewPeers();
    }

    public void requestPeers(Peer peer) {
        logger.info("Requesting peers from peer: {}", peer.getPeerId());
        Message request = networkManager.getMessageBuilder().buildPeerRequestMessage();
        peer.send(request);
    }

    public void broadcastPeerRequest() {
        logger.info("Broadcasting peer request to all connected peers");
        Message request = networkManager.getMessageBuilder().buildPeerRequestMessage();

        for (Peer peer : networkManager.getConnectedPeers().values()) {
            peer.send(request);
        }
    }
}
