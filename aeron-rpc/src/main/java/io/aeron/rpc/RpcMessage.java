package io.aeron.rpc;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Basic message format for RPC communication.
 * <pre>
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          Request ID                             |
 * +---------------------------------------------------------------+
 * |     Type      |                  Reserved                       |
 * +---------------------------------------------------------------+
 * |                      Service Name Length                        |
 * +---------------------------------------------------------------+
 * |                        Service Name                           ...
 * +---------------------------------------------------------------+
 * |                      Method Name Length                         |
 * +---------------------------------------------------------------+
 * |                         Method Name                           ...
 * +---------------------------------------------------------------+
 * |                         Payload Length                          |
 * +---------------------------------------------------------------+
 * |                           Payload                             ...
 * +---------------------------------------------------------------+
 * </pre>
 */
public class RpcMessage {
    private static final int REQUEST_ID_OFFSET = 0;
    private static final int TYPE_OFFSET = REQUEST_ID_OFFSET + Long.BYTES;
    private static final int RESERVED_OFFSET = TYPE_OFFSET + 1;
    private static final int SERVICE_NAME_LENGTH_OFFSET = RESERVED_OFFSET + 3;
    private static final int SERVICE_NAME_OFFSET = SERVICE_NAME_LENGTH_OFFSET + Integer.BYTES;

    public static final byte TYPE_REQUEST = 0x01;
    public static final byte TYPE_RESPONSE = 0x02;
    public static final byte TYPE_ERROR = 0x03;

    private final MutableDirectBuffer buffer;
    private int offset;
    private int length;

    public RpcMessage(final MutableDirectBuffer buffer) {
        this.buffer = buffer;
    }

    public static int computeLength(final String serviceName, final String methodName, final int payloadLength) {
        return SERVICE_NAME_OFFSET + 
               Integer.BYTES + serviceName.length() + 
               Integer.BYTES + methodName.length() + 
               Integer.BYTES + payloadLength;
    }

    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
        this.offset = offset;
        this.length = length;
    }

    public long requestId() {
        return buffer.getLong(offset + REQUEST_ID_OFFSET);
    }

    public void requestId(final long id) {
        buffer.putLong(offset + REQUEST_ID_OFFSET, id);
    }

    public byte type() {
        return buffer.getByte(offset + TYPE_OFFSET);
    }

    public void type(final byte type) {
        buffer.putByte(offset + TYPE_OFFSET, type);
    }

    public String serviceName() {
        final int serviceNameLength = buffer.getInt(offset + SERVICE_NAME_LENGTH_OFFSET);
        final byte[] serviceNameBytes = new byte[serviceNameLength];
        buffer.getBytes(offset + SERVICE_NAME_OFFSET, serviceNameBytes);
        return new String(serviceNameBytes);
    }

    public void serviceName(final String serviceName) {
        final byte[] serviceNameBytes = serviceName.getBytes();
        buffer.putInt(offset + SERVICE_NAME_LENGTH_OFFSET, serviceNameBytes.length);
        buffer.putBytes(offset + SERVICE_NAME_OFFSET, serviceNameBytes);
    }

    public String methodName() {
        final int serviceNameLength = buffer.getInt(offset + SERVICE_NAME_LENGTH_OFFSET);
        final int methodNameOffset = offset + SERVICE_NAME_OFFSET + serviceNameLength;
        final int methodNameLength = buffer.getInt(methodNameOffset);
        final byte[] methodNameBytes = new byte[methodNameLength];
        buffer.getBytes(methodNameOffset + Integer.BYTES, methodNameBytes);
        return new String(methodNameBytes);
    }

    public void methodName(final String methodName) {
        final int serviceNameLength = buffer.getInt(offset + SERVICE_NAME_LENGTH_OFFSET);
        final int methodNameOffset = offset + SERVICE_NAME_OFFSET + serviceNameLength;
        final byte[] methodNameBytes = methodName.getBytes();
        buffer.putInt(methodNameOffset, methodNameBytes.length);
        buffer.putBytes(methodNameOffset + Integer.BYTES, methodNameBytes);
    }

    public DirectBuffer payload() {
        final int serviceNameLength = buffer.getInt(offset + SERVICE_NAME_LENGTH_OFFSET);
        final int methodNameOffset = offset + SERVICE_NAME_OFFSET + serviceNameLength;
        final int methodNameLength = buffer.getInt(methodNameOffset);
        final int payloadOffset = methodNameOffset + Integer.BYTES + methodNameLength;
        final int payloadLength = buffer.getInt(payloadOffset);
        
        final UnsafeBuffer payloadBuffer = new UnsafeBuffer(new byte[payloadLength]);
        buffer.getBytes(payloadOffset + Integer.BYTES, payloadBuffer, 0, payloadLength);
        return payloadBuffer;
    }

    public void payload(final DirectBuffer payload) {
        final int serviceNameLength = buffer.getInt(offset + SERVICE_NAME_LENGTH_OFFSET);
        final int methodNameOffset = offset + SERVICE_NAME_OFFSET + serviceNameLength;
        final int methodNameLength = buffer.getInt(methodNameOffset);
        final int payloadOffset = methodNameOffset + Integer.BYTES + methodNameLength;
        
        buffer.putInt(payloadOffset, payload.capacity());
        buffer.putBytes(payloadOffset + Integer.BYTES, payload, 0, payload.capacity());
    }
}
