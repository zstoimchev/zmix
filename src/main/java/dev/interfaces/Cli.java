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
        else logger.error("Invalid URL. Must start with http:// or https://");
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
        Logger.disableConsole();
        System.out.println("=========================================================");
        System.out.println(" * Available commands:");
        System.out.println(" -> \\h (HELP)  - Show help");
        System.out.println(" -> \\s (STATS) - Show node statistics");
        System.out.println(" -> \\p (PEERS) - Show connected peers");
        System.out.println(" -> \\r (ROUTE) - Show active circuits and their routes");
        System.out.println(" -> \\q (QUIT)  - Exit application");
        System.out.println("=========================================================");
    }

    private void printStats() {
        System.out.println("=========================================================");
        System.out.println(" * There are:");
        System.out.println(" -> " + networkManager.getKnownPeerCount() + " known peers;");
        System.out.println(" -> " + networkManager.getConnectedPeerCount() + " connected peers;");
        System.out.println("=========================================================");
    }

    private void printPeers() {
        System.out.println("=========================================================");
        System.out.println(" * Connected peers:");
        networkManager.getConnectedPeers().forEach((uuid, peer) ->
                System.out.println(" -> " + peer.getIp() + ":" + peer.getPort() + ")"));
        System.out.println("=========================================================");
    }

    private void printCircuitRoutes() {

    }
}
