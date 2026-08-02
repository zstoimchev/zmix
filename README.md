# ZMIX — Peer-to-Peer Onion Routing

A Java 21 implementation of onion routing over a self-organizing peer-to-peer network. Each node discovers peers via gossip, builds multi-hop circuits using ECDH key exchange, and anonymizes HTTP traffic through layered AES-256-GCM encryption.

## Prerequisites

- Java 21
- Maven 3.8+ (for local builds)
- Docker + Docker Compose (for containerized setup)

## Running with Docker

The easiest way to get the network running is with Docker Compose. This builds all nodes from the same image and connects them automatically.

```bash
docker-compose up --build --scale peer=10 -d
```

This starts:
- `bootstrap-node` - the seed node on port 12137
- `config-node` - a peer on port 12128, used for sending requests
- `peer` - one relay node (scaled to 10 in the command above)

To attach to the `config-node` (or any other peer) and send an HTTP request:

```bash
docker attach config-node
```

Then type a URL and press Enter:

```
http://example.com
```

The response HTML is displayed in the console, as well as saved to the `responses/` directory.

## Running Locally

Build the fat JAR first:

```bash
mvn package
```

Then start the bootstrap node and at least three more peers in separate terminals:

```bash
java -jar target/zmix-1.0-SNAPSHOT.jar node.bootstrap.properties
java -jar target/zmix-1.0-SNAPSHOT.jar node.peer.test1.properties
java -jar target/zmix-1.0-SNAPSHOT.jar node.peer.test2.properties
java -jar target/zmix-1.0-SNAPSHOT.jar node.peer.test3.properties
```

Once enough peers have connected and the circuit is established, type a URL into any peer's terminal to send a request through the network.

IntelliJ run configurations for all nodes are included under `.idea/runConfigurations/`.

## Configuration

Each node is configured via a `.properties` file passed as the first argument. Key properties:

| Property               | Default     | Description                       |
|------------------------|-------------|-----------------------------------|
| `node.port`            | `12137`     | TCP port the node listens on      |
| `node.bootstrap`       | `false`     | Set to `true` for the seed node   |
| `bootstrap.host`       | `localhost` | Bootstrap node hostname           |
| `bootstrap.port`       | `12137`     | Bootstrap node port               |
| `circuit.length`       | `3`         | Number of hops per circuit        |
| `node.connections.max` | `5`         | Max simultaneous peer connections |
| `node.connections.min` | `3`         | Minimum connection target         |

Environment variables `NODE_PORT`, `NODE_BOOTSTRAP`, and `BOOTSTRAP_HOST` override the properties file values and are used by Docker Compose.

## Project Report

A more detailed description of the architecture, security design, evaluation, and limitations is available in the [project report](docs/REPORT.md).

## Contact

[zstoimchev@outlook.com](mailto:zstoimchev@outlook.com)

## License

© 2026 Zhivko Stoimchev. All rights reserved.