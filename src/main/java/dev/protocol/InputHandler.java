package dev.protocol;

import dev.network.CircuitManager;

import java.util.Scanner;

public class InputHandler extends Thread {
    private final Scanner scanner;
    private final CircuitManager circuitManager;

    public InputHandler(CircuitManager circuitManager) {
        this.setName("InputHandler");
        this.scanner = new Scanner(System.in);
        this.circuitManager = circuitManager;
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            System.out.print("Enter command: ");
            String input = scanner.nextLine();
            System.out.println("You entered: " + input);
            processRequest(input);
        }
        scanner.close();
    }

    private void processRequest(String input) {
        if (!circuitManager.isCircuitReady()) {
            System.out.println("No active circuit. Please try again in short.");
            circuitManager.init();
            return; // TODO: returning immediately. Maybe queue the request?
        }

        if (!isUrlValid(input)) {
            System.out.println("Invalid URL. Must start with http:// or https://");
            return;
        }

        circuitManager.sendRequest(input);
        System.out.printf("you entered '%s'%n", input);
    }

    private boolean isUrlValid(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.startsWith("https://") || url.startsWith("http://");
    }
}
