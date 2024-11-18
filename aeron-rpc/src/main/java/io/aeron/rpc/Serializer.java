package io.aeron.rpc;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Interface for serializing and deserializing objects.
 */
public interface Serializer {
    /**
     * Serialize an object to a buffer.
     *
     * @param obj object to serialize
     * @param buffer buffer to write to
     * @param offset offset in buffer to start writing
     * @return number of bytes written
     */
    int serialize(Object obj, MutableDirectBuffer buffer, int offset);

    /**
     * Deserialize an object from a buffer.
     *
     * @param buffer buffer to read from
     * @param offset offset in buffer to start reading
     * @param length number of bytes to read
     * @param type class of object to deserialize
     * @return deserialized object
     */
    <T> T deserialize(DirectBuffer buffer, int offset, int length, Class<T> type);
}
