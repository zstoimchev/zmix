package dev.network.peer;

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
}