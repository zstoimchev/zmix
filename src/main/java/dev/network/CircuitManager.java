package dev.network;

import dev.models.Message;
import dev.message.MessageBuilder;
import dev.models.PeerInfo;
import dev.models.enums.CircuitType;
import dev.utils.Logger;

import java.util.*;

public class CircuitManager {
    private final Logger logger;
    private final NetworkManager networkManager;

    private final UUID circuitId;
    private final List<PeerInfo> path;
    private CircuitType circuitType;

//     private final Map<UUID, Object> circuits;  // all circuits

    public CircuitManager(NetworkManager networkManager) {
        this.logger = Logger.getLogger(CircuitManager.class);
        this.networkManager = networkManager;

        this.circuitId = UUID.randomUUID();
        this.path = selectRandomPath(networkManager.getConfig().getCircuitLength());
        this.circuitType = null;
    }

    public void init() {
        logger.info("CircuitManager initialized");
        this.createCircuit();
    }

    private List<PeerInfo> selectRandomPath(int length) {
        List<PeerInfo> available = networkManager.getConnectionManager().getKnownPeers();
        Collections.shuffle(available);
        return available.subList(0, length);
    }

    public void createCircuit() {

        // 2. Build circuit step by step
//        buildCircuitCreate(path.get(0), path.get(1));

        // Wait for confirmation...
        waitForCircuitCreate();
//        Thread.sleep(100); // TODO: proper async handling

        // 3. Extend to second hop
//        extendCircuitTo(path, 1);

        // Wait for confirmation...
        waitForCircuitCreate();
//        Thread.sleep(100); // TODO: proper async handling

        // 4. Extend to exit node
//        extendCircuitTo(path, 2);

        // Wait for confirmation...
        waitForCircuitCreate();
//        Thread.sleep(100); // TODO: proper async handling


        this.circuitType = CircuitType.INITIAL;
        logger.info("Circuit {} built successfully using {} hops", circuitId, length);
    }

    private void buildCircuitCreate(Peer firstHop, Peer secondHop) {
        Message message = MessageBuilder.buildCircuitCreateMessageRequest(circuitId, secondHop.getPeerId().toString());
        firstHop.send(message);
    }

    private void waitForCircuitCreate() {

        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void extendCircuitTo(List<Peer> path, int hopIndex) {
        throw new UnsupportedOperationException("Not implemented yet");
    }


}