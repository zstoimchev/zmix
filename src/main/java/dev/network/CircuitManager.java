package dev.network;

import dev.message.payload.CircuitCreatePayload;
import dev.message.payload.CircuitExtendPayload;
import dev.models.Message;
import dev.message.MessageBuilder;
import dev.models.PeerInfo;
import dev.models.enums.CircuitType;
import dev.utils.CustomException;
import dev.utils.Logger;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;

public class CircuitManager {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final int circuitLength;

    private UUID circuitId;
    private List<PeerInfo> path;
    private Map<Integer, byte[]> keys;
    private final Map<Integer, KeyPair> pendingKeys;
    private CircuitType circuitType;
    private Peer entryPeer;
    private int currentHop = 0;

//     private final Map<UUID, Object> circuits;  // all circuits

    public CircuitManager(NetworkManager networkManager) {
        this.logger = Logger.getLogger(CircuitManager.class);
        this.networkManager = networkManager;
        this.circuitLength = networkManager.getConfig().getCircuitLength();
        this.circuitType = null;
        this.keys = new HashMap<>();
        this.pendingKeys = new HashMap<>();
    }

    public void init() {
        if (networkManager.getKnownPeers().size() < circuitLength) {
            logger.warn("Not enough connected peers to build circuit");
            return;
        }

        this.circuitId = UUID.randomUUID();
        this.path = selectRandomPath();
        this.currentHop = 0;

        this.createCircuit();
    }

    private List<PeerInfo> selectRandomPath() {
        List<PeerInfo> availablePeers = new ArrayList<>(networkManager.getKnownPeers());

        availablePeers.removeIf(peer -> peer.getPublicKey().equals(networkManager.getEncodedPublicKey()));

        if (availablePeers.size() < circuitLength)
            throw new CustomException("Not enough peers for circuit. Have: " + availablePeers.size() + ", Need: " + circuitLength, null);

        Collections.shuffle(availablePeers);
        return availablePeers.subList(0, circuitLength);
    }

    public void createCircuit() {
        PeerInfo entryPeerInfo = path.getFirst();
        this.entryPeer = getOrConnectToPeer(entryPeerInfo);

        if (this.entryPeer == null) {
            logger.error("Failed to connect to entry node");
            return;
        }

        KeyPair eph = networkManager.getCrypto().generateECDHKeyPair();
        pendingKeys.put(0, eph);

        Message msg = MessageBuilder.buildCircuitCreateMessageRequest(
                circuitId,
                Base64.getEncoder().encodeToString(eph.getPublic().getEncoded())
        );

        this.entryPeer.send(msg);
    }

    private Peer getOrConnectToPeer(PeerInfo peerInfo) {
        Peer existing = networkManager.getConnectedPeers().get(peerInfo.getPublicKey());

        if (existing != null) return existing;

        networkManager.connectToPeer(peerInfo.getHost(), peerInfo.getPort());

        try {
            Thread.sleep(3 * 1000); // TODO: callback webhook ? ? ?
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return networkManager.getConnectedPeers().get(peerInfo.getPublicKey());
    }

    public void onCircuitCreateResponse(Peer peer, Message message) {
        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();

        if (!payload.getCircuitId().equals(circuitId)) return;

        KeyPair eph = pendingKeys.remove(0);
        PublicKey theirPub = networkManager.getCrypto().decodePublicKey(payload.getSecretKey());

        byte[] sharedSecret = networkManager.getCrypto().performECDH(eph.getPrivate(), theirPub);

        byte[] sessionKey = networkManager.getCrypto().deriveAESKey(sharedSecret);

        keys.put(0, sessionKey);
        currentHop = 1;

        if (currentHop < circuitLength) {
            extendToNextHop(currentHop);
        } else {
            circuitType = CircuitType.INITIAL;
            logger.info("Circuit {} established", circuitId);
        }
    }

    private void extendToNextHop(int hop) {
        PeerInfo nextHop = path.get(hop);

        KeyPair eph = networkManager.getCrypto().generateECDHKeyPair();
        pendingKeys.put(hop, eph);

        CircuitExtendPayload payload = new CircuitExtendPayload(
                nextHop.getPublicKey(),
                nextHop.getHost(),
                nextHop.getPort(),
                Base64.getEncoder().encodeToString(eph.getPublic().getEncoded())
        );

        byte[] encrypted = payload.toBytes();
        for (int i = hop - 1; i >= 0; i--) {
            encrypted = networkManager.getCrypto().encryptAES(encrypted, keys.get(i));
        }

        Message msg = MessageBuilder.buildCircuitExtendMessage(circuitId, encrypted);
        entryPeer.send(msg);
    }

}