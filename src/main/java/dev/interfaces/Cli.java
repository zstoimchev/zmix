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
            logger.info("Enter URL to send request: ");
            String input = scanner.nextLine();
            processRequest(input);
        }
        scanner.close();
    }

    private void processRequest(String input) {
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
}
