package dev.protocol;

import dev.network.CircuitManager;
import dev.utils.Logger;

import java.util.Scanner;

public class InputHandler extends Thread {
    private final Logger logger;
    private final Scanner scanner;
    private final CircuitManager circuitManager;

    public InputHandler(CircuitManager circuitManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.setName("InputHandler");
        this.scanner = new Scanner(System.in);
        this.circuitManager = circuitManager;
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            logger.info("Enter URL to send request: ");
            String input = scanner.nextLine();
            logger.debug("URL to send: " + input);
            processRequest(input);
        }
        scanner.close();
    }

    private void processRequest(String input) {
        if (!circuitManager.isCircuitReady()) {
            logger.warn("No active circuit. Please try again in short.");
            circuitManager.init();
            return; // TODO: returning immediately. Maybe queue the request?
        }

        if (isUrlValid(input)) circuitManager.sendRequest(input);
        logger.error("Invalid URL. Must start with http:// or https://");
    }

    private boolean isUrlValid(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.startsWith("https://") || url.startsWith("http://");
    }
}
