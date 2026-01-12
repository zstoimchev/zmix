package dev.network;

import dev.message.payload.CircuitCreatePayload;
import dev.message.payload.CircuitExtendEncryptedPayload;
import dev.message.payload.CircuitExtendPayload;
import dev.models.Message;
import dev.message.MessageBuilder;
import dev.models.PeerInfo;
import dev.models.enums.CircuitType;
import dev.utils.Crypto;
import dev.utils.CustomException;
import dev.utils.Logger;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class CircuitManager {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final ScheduledExecutorService circuitExecutor;
    private final Crypto crypto;
    private final int circuitLength;

    @Getter
    private UUID myCircuitId;
    private List<PeerInfo> path;
    private Map<Integer, byte[]> keys;
    private final Map<Integer, KeyPair> pendingKeys;
    private CircuitType circuitType;
    private Peer entryPeer;

    private final Map<UUID, RelayCircuit> relayCircuits;
    private int currentHop = 0;

    public CircuitManager(NetworkManager networkManager) {
        this.logger = Logger.getLogger(CircuitManager.class);
        this.networkManager = networkManager;
        this.circuitExecutor = Executors.newSingleThreadScheduledExecutor();
        this.crypto = networkManager.getCrypto();
        this.circuitLength = networkManager.getConfig().getCircuitLength();
        this.circuitType = null;
        this.keys = new HashMap<>();
        this.pendingKeys = new HashMap<>();
        this.relayCircuits = new HashMap<>();
    }

    public void init() {
        logger.info("Initializing Circuit Manager. There are {} peers connected.", networkManager.getKnownPeers().size());
        if (networkManager.getKnownPeers().size() < circuitLength) {
            logger.warn("Not enough connected peers to build circuit");
            return;
        }

        if (this.circuitType == CircuitType.PENDING) {
            logger.warn("Circuit is already being prepared. Wait a bit...");
            return;
        }

        this.circuitType = CircuitType.PENDING;
        this.myCircuitId = UUID.randomUUID();
        this.path = selectRandomPath();
        this.currentHop = 0;

        circuitExecutor.submit(this::createCircuit);
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

        KeyPair eph = crypto.generateECDHKeyPair();
        pendingKeys.put(0, eph);

        Message msg = MessageBuilder.buildCircuitCreateMessageRequest(myCircuitId, Base64.getEncoder().encodeToString(eph.getPublic().getEncoded()));

        this.entryPeer.send(msg);
    }

    private Peer getOrConnectToPeer(PeerInfo peerInfo) {
        Peer existing = networkManager.getConnectedPeers().get(peerInfo.getPublicKey());
        if (existing != null) return existing;

        networkManager.connectToPeer(peerInfo.getHost(), peerInfo.getPort());

        int attempts = 0;
        while (attempts < 30) {
            try {
                Thread.sleep(100);
                Peer peer = networkManager.getConnectedPeers().get(peerInfo.getPublicKey());
                if (peer != null) return peer;
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        return null; // Timeout connecting to peer
        // TODO: handle timeout properly in the parent method
    }


    public void onCircuitCreateRequest(Peer peer, Message message) {
        logger.info("Received CIRCUIT_CREATE_REQUEST from peer: {}", peer.getPeerId());

        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        KeyPair ephemeralKeyPair = crypto.generateECDHKeyPair();
        PublicKey theirEphemeralPublicKey = crypto.decodePublicKey(payload.getEphemeralKey());

        byte[] sharedSecret = crypto.performECDH(ephemeralKeyPair.getPrivate(), theirEphemeralPublicKey);
        byte[] sessionKey = crypto.deriveAESKey(sharedSecret);

        relayCircuits.put(circuitId, new RelayCircuit(circuitId, peer, null, sessionKey));

        String ourEphemeralKeyBase64 = Base64.getEncoder().encodeToString(ephemeralKeyPair.getPublic().getEncoded());
        Message response = MessageBuilder.buildCircuitCreateMessageResponse(circuitId, ourEphemeralKeyBase64);
        peer.send(response);

        logger.info("Sent CIRCUIT_CREATED for circuit: {}", circuitId);
    }

    public void onCircuitCreateResponse(Peer peer, Message message) {
        logger.info("Received CIRCUIT_CREATED response");
        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();

        if (!payload.getCircuitId().equals(myCircuitId)) {
            logger.warn("Received response for different circuit. Expected: {}, Got: {}", myCircuitId, payload.getCircuitId());
            return;
        }

        KeyPair eph = pendingKeys.remove(0);
        PublicKey theirPub = crypto.decodePublicKey(payload.getEphemeralKey());

        byte[] sharedSecret = crypto.performECDH(eph.getPrivate(), theirPub);
        byte[] sessionKey = crypto.deriveAESKey(sharedSecret);

        keys.put(currentHop, sessionKey);
        logger.debug("Established session key with hop {}", currentHop);
        currentHop++;

        if (currentHop < circuitLength) {
            extendToNextHop(currentHop);
        } else {
            circuitType = CircuitType.INITIAL;
            logger.info("Circuit {} fully established with {} hops!", myCircuitId, circuitLength);
        }
    }

    private void extendToNextHop(int hop) {
        logger.info("Extending circuit to hop {}", hop);
        PeerInfo nextHop = path.get(hop);

        KeyPair eph = crypto.generateECDHKeyPair();
        pendingKeys.put(hop, eph);

        CircuitExtendPayload payload = new CircuitExtendPayload(
                nextHop.getPublicKey(),
                nextHop.getHost(),
                nextHop.getPort(),
                Base64.getEncoder().encodeToString(eph.getPublic().getEncoded()));

        byte[] encrypted = payload.toBytes();
        for (int i = hop - 1; i >= 0; i--) {
            encrypted = crypto.encryptAES(encrypted, keys.get(i));
        }

        Message msg = MessageBuilder.buildCircuitExtendMessageRequest(myCircuitId, encrypted);
        entryPeer.send(msg);
        logger.info("Sent CIRCUIT_EXTEND for hop {}", hop);
    }

    public void onCircuitExtendRequest(Peer peer, Message message) {
        logger.info("Received CIRCUIT_EXTEND from peer: {}", peer.getPeerId());
        CircuitExtendEncryptedPayload payload = (CircuitExtendEncryptedPayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();
        RelayCircuit relay = relayCircuits.get(circuitId);

        if (relay == null) {
            logger.warn("Unknown circuit: {}", circuitId);
            return;
        }

        byte[] decrypted = crypto.decryptAES(payload.getEncryptedData(), relay.sessionKey);

        if (relay.nextHop != null) {
            // Just forward to next hop
            Message forwardMsg = MessageBuilder.buildCircuitExtendMessageRequest(circuitId, decrypted);
            relay.nextHop.send(forwardMsg);
            logger.info("Forwarded CIRCUIT_EXTEND to next hop");
            return;
        }

        // We are the next hop target
        CircuitExtendPayload extendPayload = CircuitExtendPayload.fromBytes(decrypted);
        Peer nextPeer = getOrConnectToPeer(new PeerInfo(extendPayload.getPublicKey(), extendPayload.getHost(), extendPayload.getPort()));

        if (nextPeer == null) {
            logger.error("Failed to connect to next hop");
            return;
        }

        relay.nextHop = nextPeer;

        Message createMsg = MessageBuilder.buildCircuitCreateMessageRequest(circuitId, extendPayload.getEphemeralKey());
        nextPeer.send(createMsg);

        logger.info("Forwarded CIRCUIT_CREATE to next hop");
    }

    public void onCircuitExtendResponse(Peer peer, Message message) {
        logger.info("Received CIRCUIT_EXTENDED response");

        CircuitExtendEncryptedPayload payload = (CircuitExtendEncryptedPayload) message.getPayload();
        byte[] data = payload.getEncryptedData();

        for (int i = 0; i < currentHop; i++) {
            data = crypto.decryptAES(data, keys.get(i));
        }

        String ephemeralKeyBase64 = new String(data, StandardCharsets.UTF_8);
        KeyPair eph = pendingKeys.remove(currentHop);
        PublicKey hopPub = crypto.decodePublicKey(ephemeralKeyBase64);

        byte[] secret = crypto.performECDH(eph.getPrivate(), hopPub);
        byte[] sessionKey = crypto.deriveAESKey(secret);

        keys.put(currentHop, sessionKey);
        logger.info("Established session key with hop {}", currentHop);
        currentHop++;

        if (currentHop < circuitLength) {
            extendToNextHop(currentHop);
        } else {
            circuitType = CircuitType.INITIAL;
            logger.info("Circuit {} fully established with {} hops!", myCircuitId, circuitLength);
        }
    }

    public void onCircuitExtendResponseAsRelay(Peer from, Message message) {
        logger.info("Received CIRCUIT_EXTENDED as relay");

        CircuitExtendEncryptedPayload payload = (CircuitExtendEncryptedPayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();
        RelayCircuit relay = relayCircuits.get(circuitId);

        if (relay == null) {
            logger.warn("Unknown circuit: {}", circuitId);
            return;
        }

        byte[] responseData = payload.getEncryptedData();
        byte[] encrypted = crypto.encryptAES(responseData, relay.sessionKey);

        Message response = MessageBuilder.buildCircuitExtendMessageResponse(circuitId, encrypted);
        relay.previousHop.send(response);

        logger.info("Forwarded CIRCUIT_EXTENDED to previous hop");
    }

    public boolean isCircuitReady() {
        return circuitType == CircuitType.INITIAL && currentHop == circuitLength;
    }

    public void sendRequest(String input) {
//        construct get request, encrypt it using session keys, send it to entry peer
        return;
    }

    @AllArgsConstructor
    private static class RelayCircuit {
        UUID circuitId;
        Peer previousHop;
        Peer nextHop;
        byte[] sessionKey;
    }
}