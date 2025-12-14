package dev.network.peer;

import dev.message.Message;
import dev.message.enums.MessageType;
import dev.message.payload.HandshakePayload;
import dev.network.Event;
import dev.network.MessageQueue;
import dev.network.NetworkManager;
import dev.message.MessageSerializer;
import dev.utils.CustomException;
import dev.utils.Logger;
import lombok.Getter;

import java.io.*;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Peer implements Runnable {
    private final Logger logger;
    @Getter
    private final UUID peerId;
    private final Socket socket;
    private final PeerDirection peerDirection;
    @Getter
    private final String ip;
    @Getter
    private final int port;
    private final NetworkManager networkManager;
    private final MessageQueue messageQueue;
    private final BufferedReader in;
    private final BufferedWriter out;

    //    @Getter
    private PublicKey publicKey;
    @Getter
    private String publicKeyBase64Encoded;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);


    public Peer(Socket socket, MessageQueue queue, NetworkManager networkManager, PeerDirection peerDirection) {
        this.logger = Logger.getLogger(Peer.class);
        this.peerId = UUID.randomUUID();
        this.socket = socket;
        this.peerDirection = peerDirection;
        this.ip = socket.getLocalAddress().getHostAddress();
        this.port = socket.getPort();
        this.networkManager = networkManager;
        this.messageQueue = queue;

        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException e) {
            logger.error("Could not create input/output stream for peer. {}", e);
            throw new CustomException("Could not create input/output stream for peer. {}", e);
        }
    }

    @Override
    public void run() {
        try {
            if (socket.isClosed()) {
                logger.warn("Socket was already closed before starting the peer.");
                return;
            }

            boolean isHandshakeSuccessful = performHandshake();
            if (!isHandshakeSuccessful) {
                logger.warn("Handshake failed. Exiting");
                return;
            }

            networkManager.registerPeer(this);
            networkManager.getPeerDiscoveryProtocol().requestPeers(this); // TODO: remove reference to PeerDiscoveryProtocol
            this.isRunning.set(true);

            while (this.isRunning.get()) {
                try {
                    Message message = MessageSerializer.deserialize(in.readLine());
                    messageQueue.queue.add(new Event(this, message));
                    logger.debug("Queued message from {}", this.peerId);
                } catch (IOException e) {
                    logger.error("Could not read message from peer: " + e.getMessage(), e);
                    isRunning.set(false);
                }
            }
        } catch (Exception e) {
            logger.error("Error handling peer connection: {}", this.peerId, e);
            throw new CustomException("Error handling peer connection: " + this.peerId, e);
        } finally {
            disconnect();
        }
    }

    private boolean performHandshake() {
        try {
            if (peerDirection == PeerDirection.OUTBOUND) {
                sendHandshake();
                return waitForHandshakeResponse();
            } else {
                boolean received = waitForHandshakeResponse();
                if (received) {
                    sendHandshake();
                    return true;
                }
                return false;
            }
        } catch (Exception e) {
            logger.error("Handshake failed", e);
            return false;
        }
    }

    private void sendHandshake() {
        Message handshakeMessage = networkManager.getMessageBuilder().buildHandshakeMessage(networkManager.getEncodedPublicKey());
        this.send(handshakeMessage);
        logger.info("Sent handshake to {}", socket.getRemoteSocketAddress());
    }

    private boolean waitForHandshakeResponse() throws Exception {
        socket.setSoTimeout(5000);
        String rawMessage = in.readLine();

        if (rawMessage == null) {
            logger.warn("Connection closed during handshake");
            return false;
        }

        Message message = MessageSerializer.deserialize(rawMessage);

        if (message.getMessageType() != MessageType.HANDSHAKE) {
            logger.warn("Expected {}, got: {}", MessageType.HANDSHAKE, message.getMessageType());
            return false;
        }

        if (!(message.getPayload() instanceof HandshakePayload handshakePayload)) return false;

        // TODO: PEER CLASS SHOULD NOT DECODE THE PUBLIC KEY, MOVE THIS SOMEWHERE ELSE
        publicKeyBase64Encoded = handshakePayload.getPublicKeyBase64Encoded();
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64Encoded);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC"); // TODO: Use config for algorithm
        this.publicKey = keyFactory.generatePublic(keySpec);

        socket.setSoTimeout(0);
        logger.info("Received handshake from {}", this.peerId);
        return true;
    }

    public void send(Message message) {
        if (message.getSignature() == null) {
            message = networkManager.getCrypto().signMessage(message);
        }

        try {
            synchronized (out) {
                out.write(MessageSerializer.serialize(message) + "\n");
                out.flush();
            }
        } catch (IOException e) {
            logger.error("Could not send message to peer..." + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            socket.close();
            networkManager.unregisterPeer(this);
            logger.warn("Closed connection with peer: {}", this.peerId);
        } catch (IOException e) {
            logger.warn("Error closing connection with peer: {}", this.peerId, e);
            throw new CustomException("Error closing connection with peer: " + this.peerId, e);
        }
    }
}
