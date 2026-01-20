package dev.message.payload;

public abstract class MessagePayload {
    public abstract byte[] toBytes();

    // GPT explained why standard parsing will fail and why data length is needed
    public abstract void fromBytes(byte[] data);
}
