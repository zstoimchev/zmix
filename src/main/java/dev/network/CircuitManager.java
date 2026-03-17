package dev.network;

import dev.message.payload.CircuitCreatePayload;
import dev.message.payload.CircuitDataPayload;
import dev.message.payload.CircuitExtendPayloadEncrypted;
import dev.message.payload.CircuitExtendRequestPayload;
import dev.models.Message;
import dev.message.MessageBuilder;
import dev.models.PeerInfo;
import dev.models.enums.CircuitStatus;
import dev.utils.Crypto;
import dev.utils.CustomException;
import dev.utils.Logger;
import dev.utils.Utils;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.*;

public class CircuitManager {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final ScheduledExecutorService circuitExecutor;
    private final Crypto crypto;
    private final int circuitLength;
    private final BlockingDeque<String> requestQueue;
    private final ExecutorService requestExecutor;

    @Getter
    private UUID myCircuitId;
    private List<PeerInfo> path;
    private final Map<Integer, byte[]> keys;
    private final Map<Integer, KeyPair> pendingKeys;
    private CircuitStatus circuitType;
    private Peer entryPeer;

    private final Map<UUID, RelayCircuit> relayCircuits;
    private int currentHop = 0;

    private String lastHtmlResponse = WELCOME_HTML;
    private static final String WELCOME_HTML =
        """
        <html>
            <body style="font-family: sans-serif; padding: 40px;">
                <h1>Welcome to the P2P Network</h1>
                <p>Type a URL above and press Search.</p>
            </body>
        </html>
        """;

    public synchronized String getLastHtmlResponse() {
        return lastHtmlResponse;
    }

    private synchronized void setLastHtmlResponse(String html) {
        lastHtmlResponse = html;
        notifyAll();
    }

    public synchronized void waitForNewResponse() throws InterruptedException {
        wait();
    }

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
        this.requestQueue = new LinkedBlockingDeque<>();
        this.requestExecutor = Executors.newSingleThreadExecutor();
    }

    public void init() {
        logger.info("Initializing Circuit Manager. There are {} peers connected.", networkManager.getKnownPeers().size());
        if (networkManager.getKnownPeers().size() < circuitLength) {
            logger.warn("Not enough connected peers to build circuit");
            return;
        }

        if (this.circuitType == CircuitStatus.PENDING) {
            logger.warn("The circuit is already being prepared. Please wait a bit...");
            return;
        }

        this.circuitType = CircuitStatus.PENDING;
        this.myCircuitId = UUID.randomUUID();
        this.path = selectRandomPath();
        this.currentHop = 0;

        circuitExecutor.submit(this::createCircuit);
    }

    private List<PeerInfo> selectRandomPath() {
        List<PeerInfo> availablePeers = new ArrayList<>(networkManager.getKnownPeers());

        availablePeers.removeIf(peer -> peer.getPublicKey().equals(networkManager.getEncodedPublicKey()));

        if (availablePeers.size() < circuitLength) {
            logger.error("Not enough connected peers to build circuit. Connected: {}, Required: {}", availablePeers.size(), circuitLength);
            throw new CustomException("Not enough peers for circuit. Have: " + availablePeers.size() + ", Need: " + circuitLength, null);
        }

        Collections.shuffle(availablePeers);
        return availablePeers.subList(0, circuitLength);
    }

    public void createCircuit() {
        PeerInfo entryPeerInfo = path.getFirst();
        this.entryPeer = getOrConnectToPeer(entryPeerInfo);

        if (this.entryPeer == null) {
            logger.error("Failed to connect to entry node");
            this.circuitType = null;
            return;
        }

        KeyPair eph = crypto.generateECDHKeyPair();
        pendingKeys.put(0, eph);

        Message msg = MessageBuilder.buildCircuitCreateMessageRequest(myCircuitId, Utils.encodeBytesToString(eph.getPublic().getEncoded()));
        this.entryPeer.send(msg);
    }

    private void extendToNextHop(int hop) {
        logger.info("Extending circuit to hop {}", hop);
        PeerInfo nextHop = path.get(hop);

        KeyPair eph = crypto.generateECDHKeyPair();
        pendingKeys.put(hop, eph);

        CircuitExtendRequestPayload payload = new CircuitExtendRequestPayload(
                this.getMyCircuitId(),
                nextHop,
                Utils.encodeBytesToString(eph.getPublic().getEncoded()));

        byte[] encrypted = payload.toBytes();
        for (int i = hop - 1; i >= 0; i--) encrypted = crypto.encryptAES(encrypted, keys.get(i));
        Message message = MessageBuilder.buildCircuitExtendMessageRequest(myCircuitId, encrypted);
        entryPeer.send(message);
    }

    public void onCircuitCreateRequest(Peer peer, Message message) {
        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        KeyPair ephemeralKeyPair = crypto.generateECDHKeyPair();
        PublicKey theirEphemeralPublicKey = Crypto.decodePublicKey(payload.getEphemeralKey());

        byte[] sharedSecret = crypto.performECDH(ephemeralKeyPair.getPrivate(), theirEphemeralPublicKey);
        byte[] sessionKey = Crypto.deriveAESKey(sharedSecret);

        relayCircuits.put(circuitId, new RelayCircuit(peer, null, sessionKey));

        String myEphemeralKey = Utils.encodeBytesToString(ephemeralKeyPair.getPublic().getEncoded());
        Message response = MessageBuilder.buildCircuitCreateMessageResponse(circuitId, myEphemeralKey);

        peer.send(response);
    }

    public void onCircuitCreateResponse(Peer peer, Message message) {
        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        if (!circuitId.equals(this.getMyCircuitId())) {
            RelayCircuit relay = relayCircuits.get(circuitId);
            if (relay == null) {
                logger.warn("Unknown relay circuit {}", circuitId);
                return;
            }

            byte[] ephemeralBytes = payload.getEphemeralKey().getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = crypto.encryptAES(ephemeralBytes, relay.sessionKey);
            Message extended = MessageBuilder.buildCircuitExtendMessageResponse(circuitId, encrypted);
            relay.previousHop.send(extended);
            return;
        }

        KeyPair eph = pendingKeys.remove(0);
        PublicKey theirPub = Crypto.decodePublicKey(payload.getEphemeralKey());

        byte[] sharedSecret = crypto.performECDH(eph.getPrivate(), theirPub);
        byte[] sessionKey = Crypto.deriveAESKey(sharedSecret);

        keys.put(currentHop, sessionKey);
        logger.debug("Established session key with hop {}", currentHop);
        currentHop++;

        if (currentHop < circuitLength) {
            extendToNextHop(currentHop);
        } else {
            circuitType = CircuitStatus.ACTIVE;
            logger.info("Circuit {} fully established with {} hops!", myCircuitId, circuitLength);
            requestExecutor.submit(this::processRequestsFromQueue);
        }
    }

    public void onCircuitExtendRequest(Peer peer, Message message) {
        CircuitExtendPayloadEncrypted payload = (CircuitExtendPayloadEncrypted) message.getPayload();
        UUID circuitId = payload.getCircuitId();
        RelayCircuit relay = relayCircuits.get(circuitId);

        if (relay == null) {
            logger.warn("Received unknown circuit: {}", circuitId);
            return;
        }

        byte[] decrypted = crypto.decryptAES(payload.getEncryptedData(), relay.sessionKey);
        if (relay.nextHop != null) {
            Message forwardMessage = MessageBuilder.buildCircuitExtendMessageRequest(circuitId, decrypted);
            relay.nextHop.send(forwardMessage);
            return;
        }

        CircuitExtendRequestPayload extendPayload = CircuitExtendRequestPayload.fromBytes(decrypted);
        Peer nextPeer = getOrConnectToPeer(extendPayload.getPeerInfo());

        if (nextPeer == null) {
            logger.error("Failed to connect to next hop. Circuit involved: {}", circuitId);
            return;
        }

        Message createMessage = MessageBuilder.buildCircuitCreateMessageRequest(circuitId, extendPayload.getEphemeralKey());
        nextPeer.send(createMessage);
        relay.nextHop = nextPeer;
    }

    public void onCircuitExtendResponse(Peer peer, Message message) {
        CircuitExtendPayloadEncrypted payload = (CircuitExtendPayloadEncrypted) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        if (!circuitId.equals(this.getMyCircuitId())) {
            RelayCircuit relay = relayCircuits.get(circuitId);
            if (relay == null) {
                logger.warn("Unknown relay circuit {}", circuitId);
                return;
            }

            byte[] encryptedData = payload.getEncryptedData();
            byte[] encrypted = crypto.encryptAES(encryptedData, relay.sessionKey);
            Message extended = MessageBuilder.buildCircuitExtendMessageResponse(circuitId, encrypted);
            relay.previousHop.send(extended);
            return;
        }

        byte[] data = payload.getEncryptedData();
        for (int i = 0; i < currentHop; i++) data = crypto.decryptAES(data, keys.get(i));

        String ephemeralKey = new String(data, StandardCharsets.UTF_8);
        KeyPair eph = pendingKeys.remove(currentHop);
        PublicKey hopPub = Crypto.decodePublicKey(ephemeralKey);

        byte[] secret = crypto.performECDH(eph.getPrivate(), hopPub);
        byte[] sessionKey = Crypto.deriveAESKey(secret);

        keys.put(currentHop, sessionKey);
        logger.info("Established session key with hop {}", currentHop);
        currentHop++;

        if (currentHop < circuitLength) {
            extendToNextHop(currentHop);
        } else {
            circuitType = CircuitStatus.ACTIVE;
            logger.info("Circuit {} fully established with {} hops!", myCircuitId, circuitLength);
            requestExecutor.submit(this::processRequestsFromQueue);
        }
    }

    public void onDataTransferRequest(Peer peer, Message message) {
        CircuitDataPayload payload = (CircuitDataPayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        // if nextHop is null, send as exit node
        RelayCircuit relay = relayCircuits.get(circuitId);
        if (relay == null) {
            logger.warn("Unknown relay circuit {}", circuitId);
            return;
        }

        byte[] decrypted = crypto.decryptAES(payload.getData(), relay.sessionKey);

        if (relay.nextHop == null) {
            try {
                byte[] response = Utils.sendHttpRequest(payload.getHost(), payload.getPort(), decrypted);
                byte[] encryptedResponse = crypto.encryptAES(response, relay.sessionKey);
                Message responseMessage = MessageBuilder.buildDataTransferMessageResponse(
                        circuitId,
                        encryptedResponse
                );
                relay.previousHop.send(responseMessage);
                return;
            } catch (IOException e) {
                throw new CustomException("Failed to send as exit node", e);
            }
        }

        Message dataMessage = MessageBuilder.buildDataTransferMessageRequest(
                circuitId,
                payload.getHost(),
                payload.getPort(),
                decrypted
        );
        relay.nextHop.send(dataMessage);
    }

    public void onDataTransferResponse(Peer peer, Message message) {
        CircuitDataPayload payload = (CircuitDataPayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        if (!circuitId.equals(this.getMyCircuitId())) {
            RelayCircuit relay = relayCircuits.get(circuitId);
            if (relay == null) {
                logger.warn("Unknown relay circuit {}", circuitId);
                return;
            }

            byte[] encryptedData = crypto.encryptAES(payload.getData(), relay.sessionKey);
            Message responseMessage = MessageBuilder.buildDataTransferMessageResponse(circuitId, encryptedData);
            relay.previousHop.send(responseMessage);
            return;
        }

        byte[] decrypted = decrypt(payload.getData());
        String response = new String(decrypted, StandardCharsets.UTF_8);

        try {
            String fileName = "responses/" + System.nanoTime() + ".html";
            String html = Utils.saveHttpResponseNaive(response, fileName);
            setLastHtmlResponse(html);
            logger.info("Saved HTTP response to file: {}", fileName);
        } catch (FileNotFoundException e) {
            logger.error("Failed to save HTTP response to file", e);
            throw new RuntimeException(e);
        }
    }

    public void sendRequestToQueue(String input) {
        if (!isCircuitReady()) this.init();
        this.requestQueue.add(input);
    }

    private void processRequestsFromQueue() {
        while (true) {
            try {
                String input = requestQueue.take();
                processRequest(input);
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void processRequest(String input) {
        URI uri = URI.create(input);
        String host = uri.getHost();
        String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
        String port = scheme.equalsIgnoreCase("https") ? "443" : "80";

        String http = Utils.buildHttpGet(host);

        byte[] requestBytes = http.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedRequest = encrypt(requestBytes);

        Message dataMessage = MessageBuilder.buildDataTransferMessageRequest(
                this.getMyCircuitId(),
                host,
                port,
                encryptedRequest
        );

        sendToEntry(dataMessage);
    }

    private byte[] encrypt(byte[] requestBytes) {
        byte[] encrypted = requestBytes;
        for (int i = currentHop - 1; i >= 0; i--) {
            encrypted = crypto.encryptAES(encrypted, keys.get(i));
        }
        return encrypted;
    }

    private byte[] decrypt(byte[] responseBytes) {
        byte[] decrypted = responseBytes;
        for (int i = 0; i < currentHop; i++) {
            decrypted = crypto.decryptAES(decrypted, keys.get(i));
        }
        return decrypted;
    }

    private void sendToEntry(Message message) {
        entryPeer.send(message);
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
                logger.error("Thread was interrupted while waiting for peer connection.", e);
                return null;
            }
        }
        return null;
    }

    public boolean isCircuitReady() {
        return circuitType == CircuitStatus.ACTIVE && currentHop == circuitLength;
    }

    @AllArgsConstructor
    private static class RelayCircuit {
        Peer previousHop;
        Peer nextHop;
        byte[] sessionKey;
    }
}