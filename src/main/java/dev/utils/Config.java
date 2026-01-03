package dev.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Logger logger = Logger.getLogger(Config.class);
    private final Properties properties;

    public Config(Properties properties) {
        this.properties = properties;
    }

    public static Config load(String filename) {
        Properties properties = new Properties();

        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)) {

            if (in == null) {
                logger.error("No config file was provided. Usage: java Main <config-file>");
                throw new CustomException("Resource not found on classpath: " + filename, null);
            }

            properties.load(in);

        } catch (IOException e) {
            logger.error("Could not load config file: {}", filename, e);
            throw new CustomException("Could not load file: " + filename, e);
        }

        return new Config(properties);
    }

    public int getNodePort() {
        String envPort = System.getenv("NODE_PORT");
        return envPort != null ? Integer.parseInt(envPort) : Integer.parseInt(properties.getProperty("node.port", "12137"));
    }

    public boolean isBootstrapNode() {
        String envBootstrap = System.getenv("NODE_BOOTSTRAP");
        return envBootstrap != null ? Boolean.parseBoolean(envBootstrap) : Boolean.parseBoolean(properties.getProperty("node.bootstrap", "false"));
    }

    public int getMaxConnections() {
        return Integer.parseInt(properties.getProperty("node.connections.max", "5"));
    }

    public int getMinConnections() {
        return Integer.parseInt(properties.getProperty("node.connections.min", "3"));
    }

    public String getBootstrapNodeHost() {
        String envBootstrapHost = System.getenv("BOOTSTRAP_HOST");
        return envBootstrapHost != null ? envBootstrapHost : properties.getProperty("bootstrap.host", "localhost");
    }

    public int getBootstrapNodePort() {
        return Integer.parseInt(properties.getProperty("bootstrap.port", "12137"));
    }

    public int getPeerDiscoveryInitialDelayInSeconds() {
        return Integer.parseInt(properties.getProperty("peer.discovery.init", "30"));
    }

    public int getPeerDiscoveryDelayInSeconds() {
        return Integer.parseInt(properties.getProperty("peer.discovery.delay", "60"));
    }

    public int getConnectionMaintenanceInitialDelayInSeconds() {
        return Integer.parseInt(properties.getProperty("connection.maintenance.init", "30"));
    }

    public int getConnectionMaintenanceDelayInSeconds() {
        return Integer.parseInt(properties.getProperty("connection.maintenance.delay", "60"));
    }

    public int getCircuitLength() {
        return Integer.parseInt(properties.getProperty("circuit.length", "3"));
    }

    // TODO: method for verifying config values (integers specifically)
}