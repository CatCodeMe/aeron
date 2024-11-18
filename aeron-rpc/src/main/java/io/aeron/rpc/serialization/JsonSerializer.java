package io.aeron.rpc.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * JSON implementation of Serializer using Jackson.
 */
public class JsonSerializer implements Serializer {
    private static final String CONTENT_TYPE = "application/json";
    private final ObjectMapper objectMapper;

    public JsonSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    public JsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public DirectBuffer serialize(Object obj) throws SerializationException {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(obj);
            return new UnsafeBuffer(bytes);
        } catch (Exception e) {
            throw new SerializationException("Failed to serialize object to JSON", e);
        }
    }

    @Override
    public <T> T deserialize(DirectBuffer buffer, Class<T> type) throws SerializationException {
        try {
            byte[] bytes = new byte[buffer.capacity()];
            buffer.getBytes(0, bytes);
            return objectMapper.readValue(bytes, type);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize JSON to object", e);
        }
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    /**
     * Get the ObjectMapper instance used by this serializer.
     *
     * @return ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
