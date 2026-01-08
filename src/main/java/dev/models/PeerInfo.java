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

    @Override
    public String toString() {
        return "Public Key: " + publicKey;
    }
}