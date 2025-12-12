package dev.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.message.Message;

public class MessageSerializer {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    public static String serialize(Message message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new CustomException("Serialization error", e);
        }
    }

    public static Message deserialize(String rawMessage) {
        try {
            return objectMapper.readValue(rawMessage, Message.class);
        } catch (JsonProcessingException e) {
            throw new CustomException("Deserialization error", e);
        }
    }
}
