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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
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
    }

    public void init() {
        logger.info("Initializing Circuit Manager. There are {} peers connected.", networkManager.getKnownPeers().size());
        if (networkManager.getKnownPeers().size() < circuitLength) {
            logger.warn("Not enough connected peers to build circuit");
            return;
        }

        if (this.circuitType == CircuitStatus.PENDING) {
            logger.warn("Circuit is already being prepared. Wait a bit...");
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
        if (!this.isCircuitReady()) {
            logger.warn("No active circuit. Please try again in short.");
            this.init();
            return; // TODO: returning immediately. Maybe queue the request?
        }

        URI uri = URI.create(input);
        String host = uri.getHost();
        logger.debug("the host is {}", host);
//        String path = uri.getRawPath();
//        if (path == null || path.isEmpty()) path = "/";                 // todo
//        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery(); // todo

        String http =   "GET / HTTP/1.1\r\n" +
                        "Host: " + host + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";

        byte[] requestBytes = http.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedRequest = encryptRequest(requestBytes);

        Message dataMessage = MessageBuilder.buildDataTransferMessageRequest(
                this.getMyCircuitId(),
                host,
                "80",
                encryptedRequest
        );

        send(dataMessage);
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
        Socket s = new Socket(InetAddress.getByName(host), Integer.parseInt(port));

        s.getOutputStream().write(httpRequestData);
        s.getOutputStream().flush();

        StringBuilder response = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        String line;
        while((line = br.readLine()) != null) response.append(line).append("\r\n");
        br.close();
        s.close();

        logger.debug("the response is {}", response.toString());

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

        byte[] decrypted = decryptResponse(payload.getData());
        String response = new String(decrypted, StandardCharsets.UTF_8);

        logger.info("============================== HTTP RESPONSE ==============================");
        logger.info(response);
        logger.info("===========================================================================");
    }

    private byte[] encryptRequest(byte[] requestBytes) {
        byte[] encrypted = requestBytes;
        for (int i = currentHop - 1; i >= 0; i--) {
            encrypted = crypto.encryptAES(encrypted, keys.get(i));
        }
        return encrypted;
    }

    private byte[] decryptResponse(byte[] responseBytes) {
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