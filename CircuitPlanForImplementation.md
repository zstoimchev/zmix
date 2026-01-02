# Idea

- In `CircuitManager`, when initializing to create a circuit, choose the UUID, the path, and the entry node. Send initialization to the entry node.
- The entry node receives and handles the initialization using CircuitProtocol. Registers the circuit and does stuff, sends response back circuit create response.
- The initialization peer receives this response in the CircuitManager, and extends the circuit to the enxt hop
- The same scenario repeats, meaning messages are handled in both CircuitManager and CircuitProtocol.

This is the rough logic and needs a little more work.

---

Implementing AES is the key...