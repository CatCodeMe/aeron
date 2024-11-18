package io.aeron.rpc.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonSerializerTest {
    private JsonSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JsonSerializer();
    }

    @Test
    void testSerializeAndDeserialize() {
        TestObject original = new TestObject("test", 42);
        DirectBuffer buffer = serializer.serialize(original);
        TestObject deserialized = serializer.deserialize(buffer, TestObject.class);

        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getValue(), deserialized.getValue());
    }

    @Test
    void testSerializeNull() {
        assertThrows(SerializationException.class, () -> serializer.serialize(null));
    }

    @Test
    void testDeserializeInvalidJson() {
        byte[] invalidJson = "{invalid}".getBytes();
        DirectBuffer buffer = serializer.serialize(invalidJson);
        
        assertThrows(SerializationException.class, () -> 
            serializer.deserialize(buffer, TestObject.class));
    }

    @Test
    void testCustomObjectMapper() {
        ObjectMapper customMapper = new ObjectMapper();
        JsonSerializer customSerializer = new JsonSerializer(customMapper);
        
        assertSame(customMapper, customSerializer.getObjectMapper());
    }

    @Test
    void testGetContentType() {
        assertEquals("application/json", serializer.getContentType());
    }

    // Test data class
    private static class TestObject {
        private String name;
        private int value;

        public TestObject() {
            // Default constructor for Jackson
        }

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}
