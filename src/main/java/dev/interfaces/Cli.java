package dev.interfaces;

import dev.network.CircuitManager;
import dev.network.NetworkManager;
import dev.utils.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;

public class Cli extends Thread {
    private final Logger logger;
    private final Scanner scanner;
    private final NetworkManager networkManager;
    private final CircuitManager circuitManager;

    public Cli(NetworkManager networkManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.setName("InputHandler");
        this.scanner = new Scanner(System.in);
        this.networkManager = networkManager;
        this.circuitManager = networkManager.getCircuitManager();
    }

    @Override
    public void run() {
        try {
            networkManager.waitUntilReady();
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for network manager");
            return;
        }
        while (!this.isInterrupted()) {
            String input = scanner.nextLine();
            processRequest(input);
        }
        scanner.close();
    }

    private void processRequest(String input) {
        switch (input) {
            case "\\h":
                Logger.disableConsole();
                printHelp();
                return;
            case "\\d":
                Logger.disableConsole();
                return;
            case "\\c":
                Logger.enableConsole();
                return;
            case "\\s":
                printStats();
                return;
            case "\\p":
                printPeers();
                return;
            case "\\r":
                printCircuitRoutes();
                return;
            case "\\q":
                logger.info("Exiting application...");
                System.exit(0);
                return;
        }

        if (isUrlValid(input)) circuitManager.sendRequestToQueue(input);
        else logger.printToConsole("Invalid URL. Must start with http:// or https://");
    }

    private boolean isUrlValid(String url) {
        if (url == null || url.isEmpty()) return false;

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private void printHelp() {
        logger.printToConsole("""
                ====================================================================================================
                Welcome to the CLI help menu!
                You can enter a URL to send a request through the network, or use one of the following commands:
                 -> \\h (HELP)  - Show help
                 -> \\s (STATS) - Show node statistics
                 -> \\p (PEERS) - Show connected peers
                 -> \\r (ROUTE) - Show active circuits and their routes
                 -> \\q (QUIT)  - Exit application
                Note: When entering a URL, it must start with http:// or https://
                ====================================================================================================
                """);
    }

    private void printStats() {
        logger.printToConsole("""
                =========================================================
                There are:
                 -> {} known peers
                 -> {} connected peers
                =========================================================
                """, networkManager.getKnownPeerCount(), networkManager.getConnectedPeerCount());
    }

    private void printPeers() {
        StringBuilder sb = new StringBuilder("""
            =========================================================
            The connected peers are:
            """);

        networkManager.getConnectedPeers().forEach((uuid, peer) ->
                sb.append(" -> ")
                        .append(peer.getIp())
                        .append(':')
                        .append(peer.getPort())
                        .append('\n'));

        sb.append("=========================================================\n");

        logger.printToConsole(sb.toString());
    }

    private void printCircuitRoutes() {
        String myCircuitRoute = circuitManager.getMyCircuitRoute();
        String relayCircuitRoutes = circuitManager.getRelayCircuitRoutes();

        logger.printToConsole("""
                =========================================================
                Active circuits and their routes (mine):
                 -> {}
                ---------------------------------------------------------
                Active circuits and their routes (relay):
                 -> {}
                =========================================================
                """, myCircuitRoute, relayCircuitRoutes);
    }
}
