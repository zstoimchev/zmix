package dev.onion;

import dev.models.Message;
import dev.message.MessageBuilder;
import dev.network.NetworkManager;
import dev.network.Peer;
import dev.utils.Crypto;
import dev.utils.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CircuitManager {
    private final Logger logger;
    private final NetworkManager networkManager;
    private final Crypto crypto;
    private final Map<String, Circuit> circuits;

    public CircuitManager(NetworkManager networkManager, MessageBuilder messageBuilder, Crypto crypto) {
        this.logger = Logger.getLogger(CircuitManager.class);
        this.networkManager = networkManager;
        this.crypto = crypto;
        this.circuits = new ConcurrentHashMap<>();
    }

    public Circuit createCircuit(int length) {
        List<Peer> path = selectRandomPath(length);
        String circuitId = UUID.randomUUID().toString();

        // 2. Build circuit step by step
        buildCircuitCreate(circuitId, path.get(0), path.get(1));

        // Wait for confirmation...
        waitForCircuitCreate(circuitId);
//        Thread.sleep(100); // TODO: proper async handling

        // 3. Extend to second hop
        extendCircuitTo(circuitId, path, 1);

        // Wait for confirmation...
        waitForCircuitCreate(circuitId);
//        Thread.sleep(100); // TODO: proper async handling

        // 4. Extend to exit node
        extendCircuitTo(circuitId, path, 2);

        // Wait for confirmation...
        waitForCircuitCreate(circuitId);
//        Thread.sleep(100); // TODO: proper async handling

        Circuit circuit = new Circuit(circuitId, path);
        circuits.put(circuitId, circuit);

        logger.info("Circuit {} built successfully using {} hops", circuitId, length);
        return circuit;
    }

    private void waitForCircuitCreate(String circuitId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private List<Peer> selectRandomPath(int length) {
        List<Peer> available = new ArrayList<>(networkManager.getConnectionManager().getConnectedPeers().values());

        if (available.size() < length) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Collections.shuffle(available);
        return available.subList(0, length);
    }

    private void buildCircuitCreate(String circuitId, Peer firstHop, Peer secondHop) {
        Message message = MessageBuilder.buildCircuitCreateMessageRequest(circuitId, secondHop.getPeerId().toString());
        firstHop.send(message);
    }

    private void extendCircuitTo(String circuitId, List<Peer> path, int hopIndex) {
//        // Build message for the target hop
//        Peer targetPeer = path.get(hopIndex);
//        Peer nextPeer = (hopIndex < path.size() - 1) ? path.get(hopIndex + 1) : null;
//
//        Message innerMsg = messageBuilder.buildCircuitExtendMessage(circuitId, nextPeer != null ? nextPeer.getPeerId() : null, nextPeer == null // is exit node
//        );
//
//        // Encrypt in layers (backwards from target to first hop)
//        byte[] data = MessageSerializer.serialize(innerMsg).getBytes();
//
//        for (int i = hopIndex; i >= 0; i--) {
//            data = crypto.encrypt(data, path.get(i).getPublicKey());
//        }
//
//        // Send through first hop
//        Message relayMsg = new Message(MessageType.RELAY_EXTEND, circuitId, data);
//        path.get(0).send(relayMsg);
    }


}