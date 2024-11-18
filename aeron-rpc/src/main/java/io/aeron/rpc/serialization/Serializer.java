package io.aeron.rpc.serialization;

import org.agrona.DirectBuffer;

/**
 * Interface for serializing and deserializing objects.
 */
public interface Serializer {
    /**
     * Serialize an object to bytes.
     *
     * @param obj object to serialize
     * @return serialized bytes
     * @throws SerializationException if serialization fails
     */
    DirectBuffer serialize(Object obj) throws SerializationException;

    /**
     * Deserialize bytes to an object.
     *
     * @param buffer buffer containing serialized data
     * @param type target type class
     * @param <T> target type
     * @return deserialized object
     * @throws SerializationException if deserialization fails
     */
    <T> T deserialize(DirectBuffer buffer, Class<T> type) throws SerializationException;

    /**
     * Get the content type for this serializer (e.g. "application/json").
     *
     * @return content type string
     */
    String getContentType();
}
