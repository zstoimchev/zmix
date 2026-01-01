package dev.network;

import dev.models.Message;
import dev.message.MessageBuilder;
import dev.models.PeerInfo;
import dev.models.enums.CircuitType;
import dev.models.enums.MessageType;
import dev.utils.CustomException;
import dev.utils.Logger;

import java.security.KeyPair;
import java.util.*;

public class CircuitManager {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final int circuitLength;

    private UUID circuitId;
    private List<PeerInfo> path;
    private Map<Integer, byte[]> keys;
    private CircuitType circuitType;
    private Peer entryPeer;

//     private final Map<UUID, Object> circuits;  // all circuits

    public CircuitManager(NetworkManager networkManager) {
        this.logger = Logger.getLogger(CircuitManager.class);
        this.networkManager = networkManager;
        this.circuitLength = networkManager.getConfig().getCircuitLength();
        this.circuitType = null;
        this.keys = new HashMap<>();
    }

    public void init() {
        if (networkManager.getKnownPeers().size() < circuitLength) {
            logger.warn("Not enough connected peers to build circuit");
            return;
        }

        logger.info("CircuitManager initialized");

        this.circuitId = UUID.randomUUID();
        this.path = selectRandomPath();
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
        PeerInfo entryPeer = path.getFirst();
        this.entryPeer = getOrConnectToPeer(entryPeer);

        if (this.entryPeer == null) {
            logger.error("Failed to connect to entry node");
            return;
        }

        byte[] entrySessionKey = establishSessionKeyWithEntry();
        keys.put(0, entrySessionKey);

        byte[] middleSessionKey = extendCircuitToHop(1);
        keys.put(1, middleSessionKey);

        byte[] exitSessionKey = extendCircuitToHop(2);
        keys.put(2, exitSessionKey);

        this.circuitType = CircuitType.INITIAL;
        logger.info("Circuit {} built successfully using {} hops", circuitId, circuitLength);
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















    private byte[] establishSessionKeyWithEntry() {
        // Generate ephemeral key pair for ECDH
        KeyPair ephemeralKeyPair = networkManager.getCrypto().generateECDHKeyPair();

        // Send CIRCUIT_CREATE to entry node with our ephemeral public key
        Message createMsg = MessageBuilder.buildCircuitCreateMessageRequest(
                circuitId,
                Base64.getEncoder().encodeToString(ephemeralKeyPair.getPublic().getEncoded())
        );
        entryPeer.send(createMsg);

        // TODO: proper async handling
//        Message response = waitForResponse(MessageType.CIRCUIT_CREATED);

        // Extract entry node's ephemeral public key from response
        String entryEphemeralKeyBase64 = response.getPayload().getEphemeralPublicKey();
        PublicKey entryEphemeralPublicKey = decodePublicKey(entryEphemeralKeyBase64);

        // Perform ECDH to get shared secret
        byte[] sharedSecret = networkManager.getCrypto().performECDH(
                ephemeralKeyPair.getPrivate(),
                entryEphemeralPublicKey
        );

        // Derive AES key from shared secret
        return networkManager.getCrypto().deriveAESKey(sharedSecret);
    }

    private byte[] extendCircuitToHop(int hopIndex) {
        PeerInfo nextHop = pathInfo.get(hopIndex);

        // Generate new ephemeral key pair for this hop
        KeyPair ephemeralKeyPair = networkManager.getCrypto().generateECDHKeyPair();

        // Build extend payload (unencrypted)
        CircuitExtendPayload extendPayload = new CircuitExtendPayload(
                nextHop.getPublicKey(),
                nextHop.getHost(),
                nextHop.getPort(),
                Base64.getEncoder().encodeToString(ephemeralKeyPair.getPublic().getEncoded())
        );

        // Encrypt the extend payload in layers (from innermost to outermost)
        byte[] encrypted = extendPayload.toBytes();

        // Encrypt with each hop's session key (backwards from target hop to entry)
        for (int i = hopIndex - 1; i >= 0; i--) {
            encrypted = networkManager.getCrypto().encryptAES(encrypted, sessionKeys.get(i));
        }

        // Send CIRCUIT_EXTEND through entry node
        Message extendMsg = MessageBuilder.buildCircuitExtendMessage(circuitId, encrypted);
        entryPeer.send(extendMsg);

        // Wait for CIRCUIT_EXTENDED response
        Message response = waitForResponse(MessageType.CIRCUIT_EXTENDED);

        // Decrypt response layers
        byte[] decrypted = response.getPayload().getEncryptedData();
        for (int i = 0; i < hopIndex; i++) {
            decrypted = networkManager.getCrypto().decryptAES(decrypted, sessionKeys.get(i));
        }

        // Extract ephemeral public key from response
        String hopEphemeralKeyBase64 = extractEphemeralKey(decrypted);
        PublicKey hopEphemeralPublicKey = decodePublicKey(hopEphemeralKeyBase64);

        // Perform ECDH
        byte[] sharedSecret = networkManager.getCrypto().performECDH(
                ephemeralKeyPair.getPrivate(),
                hopEphemeralPublicKey
        );

        return networkManager.getCrypto().deriveAESKey(sharedSecret);
    }

    private Message waitForResponse(MessageType expectedType) {
        // TODO: Implement proper async waiting with timeout
        throw new UnsupportedOperationException("Async response handling not implemented");
    }


}