package dev.models;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PeerInfo {
    public String publicKey;
    public String host;
    public Integer port;

    public PeerInfo(String publicKey, String host, Integer port) {
        this.publicKey = publicKey;
        this.host = host;
        this.port = port;
    }

    public String serialize() {
        return publicKey + ";" + host + ";" + port;
    }

    public static PeerInfo deserialize(String s) {
        String[] parts = s.split(";", 3);
        return new PeerInfo(parts[0], parts[1], Integer.parseInt(parts[2]));
    }

    @Override
    public String toString() {
        return "Public Key: " + publicKey;
    }
}