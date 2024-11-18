package io.aeron.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * JSON-based implementation of the Serializer interface using Jackson.
 */
public class JsonSerializer implements Serializer {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int serialize(Object obj, MutableDirectBuffer buffer, int offset) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(obj);
            buffer.putBytes(offset, bytes);
            return bytes.length;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    @Override
    public <T> T deserialize(DirectBuffer buffer, int offset, int length, Class<T> type) {
        try {
            byte[] bytes = new byte[length];
            buffer.getBytes(offset, bytes);
            return objectMapper.readValue(bytes, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }
}
