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
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
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
                logger.error("Thread was interrupted while waiting for peer connection.", e);
                return null;
            }
        }
        return null;
    }

    public void onCircuitCreateRequest(Peer peer, Message message) {
        CircuitCreatePayload payload = (CircuitCreatePayload) message.getPayload();
        UUID circuitId = payload.getCircuitId();

        KeyPair ephemeralKeyPair = crypto.generateECDHKeyPair();
        PublicKey theirEphemeralPublicKey = crypto.decodePublicKey(payload.getEphemeralKey());

        byte[] sharedSecret = crypto.performECDH(ephemeralKeyPair.getPrivate(), theirEphemeralPublicKey);
        byte[] sessionKey = crypto.deriveAESKey(sharedSecret);

        relayCircuits.put(circuitId, new RelayCircuit(peer, null, sessionKey));

        String ourEphemeralKeyBase64 = Base64.getEncoder().encodeToString(ephemeralKeyPair.getPublic().getEncoded());
        Message response = MessageBuilder.buildCircuitCreateMessageResponse(circuitId, ourEphemeralKeyBase64);

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
        PublicKey theirPub = crypto.decodePublicKey(payload.getEphemeralKey());

        byte[] sharedSecret = crypto.performECDH(eph.getPrivate(), theirPub);
        byte[] sessionKey = crypto.deriveAESKey(sharedSecret);

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

    private void extendToNextHop(int hop) {
        logger.info("Extending circuit to hop {}", hop);
        PeerInfo nextHop = path.get(hop);

        KeyPair eph = crypto.generateECDHKeyPair();
        pendingKeys.put(hop, eph);

        CircuitExtendRequestPayload payload = new CircuitExtendRequestPayload(
                this.getMyCircuitId(),
                nextHop,
                Base64.getEncoder().encodeToString(eph.getPublic().getEncoded()));

        byte[] encrypted = payload.toBytes();
        for (int i = hop - 1; i >= 0; i--) encrypted = crypto.encryptAES(encrypted, keys.get(i));
        Message message = MessageBuilder.buildCircuitExtendMessageRequest(myCircuitId, encrypted);
        entryPeer.send(message);
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
            circuitType = CircuitStatus.ACTIVE;
            logger.info("Circuit {} fully established with {} hops!", myCircuitId, circuitLength);
        }
    }

    public boolean isCircuitReady() {
        return circuitType == CircuitStatus.ACTIVE && currentHop == circuitLength;
    }

    public void sendRequest(String input) {
        this.requestQueue.add(input);
    }

    public void processRequest(String input) {
        URI uri = URI.create(input);
        String host = uri.getHost();
        String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
        String port = scheme.equalsIgnoreCase("https") ? "443" : "80";

        String http = "GET / HTTP/1.1\r\n" +
                "Host: " + host + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";

        byte[] requestBytes = http.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedRequest = encrypt(requestBytes);

        Message dataMessage = MessageBuilder.buildDataTransferMessageRequest(
                this.getMyCircuitId(),
                host,
                port,
                encryptedRequest
        );

        send(dataMessage);
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
                byte[] response = sendAsExitNode(payload.getHost(), payload.getPort(), decrypted);
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

    private byte[] sendAsExitNode(String host, String port, byte[] httpRequestData) throws IOException {
        int portNum = Integer.parseInt(port);
        Socket socket;

        if (portNum == 443) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = factory.createSocket(host, portNum);
            logger.debug("Created SSL socket to {}:{}", host, portNum);
        } else {
            socket = new Socket(InetAddress.getByName(host), portNum);
            logger.debug("Created plain socket to {}:{}", host, portNum);
        }

        socket.getOutputStream().write(httpRequestData);
        socket.getOutputStream().flush();

        StringBuilder response = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) response.append(line).append("\r\n");
        br.close();
        socket.close();
        return response.toString().getBytes(StandardCharsets.UTF_8);
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

        saveHttpResponseNaive(response);
    }

    private void saveHttpResponse(String response) {
        try {
            int bodyStart = response.indexOf("\r\n\r\n");
            if (bodyStart == -1) bodyStart = response.indexOf("\n\n");

            String html = (bodyStart != -1) ? response.substring(bodyStart + 4) : response;

            String filename = "responses/" + System.nanoTime() + ".html";
            try (PrintWriter out = new PrintWriter(filename)) {
                out.println(html);
            }

            logger.info("Saved response to: {}", filename);

        } catch (FileNotFoundException e) {
            logger.error("Failed to save: {}", e.getMessage());
        }
    }

    private void saveHttpResponseNaive(String response) {
        try {
            String html = extractHtml(response);
            String filename = "responses/" + System.nanoTime() + ".html";
            try (PrintWriter out = new PrintWriter(filename)) {
                out.println(html);
            }
            logger.info("Response saved in file: {}", filename);
        } catch (FileNotFoundException e) {
            logger.error("Failed to save: {}", e.getMessage());
        }
    }

    private String extractHtml(String response) {
        String lowercaseResponse = response.toLowerCase();
        int start = lowercaseResponse.indexOf("<!doctype html");
        if (start == -1) start = lowercaseResponse.indexOf("<html");
        return (start != -1) ? response.substring(start) : response;
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

    private void send(Message message) {
        entryPeer.send(message);
    }

    @AllArgsConstructor
    private static class RelayCircuit {
        Peer previousHop;
        Peer nextHop;
        byte[] sessionKey;
    }
}