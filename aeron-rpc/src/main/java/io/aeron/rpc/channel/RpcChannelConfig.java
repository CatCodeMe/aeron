package io.aeron.rpc.channel;

import io.aeron.rpc.RpcPattern;

/**
 * Configuration for RPC communication channels.
 */
public class RpcChannelConfig {
    private final String channel;
    private final int streamId;
    private final RpcPattern pattern;
    private final int sessionId;
    private final boolean exclusive;

    private RpcChannelConfig(Builder builder) {
        this.channel = builder.channel;
        this.streamId = builder.streamId;
        this.pattern = builder.pattern;
        this.sessionId = builder.sessionId;
        this.exclusive = builder.exclusive;
    }

    public String getChannel() {
        return channel;
    }

    public int getStreamId() {
        return streamId;
    }

    public RpcPattern getPattern() {
        return pattern;
    }

    public int getSessionId() {
        return sessionId;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String channel;
        private int streamId;
        private RpcPattern pattern = RpcPattern.ONE_TO_ONE; // Default pattern
        private int sessionId = 0;
        private boolean exclusive = false;

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder streamId(int streamId) {
            this.streamId = streamId;
            return this;
        }

        public Builder pattern(RpcPattern pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder sessionId(int sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder exclusive(boolean exclusive) {
            this.exclusive = exclusive;
            return this;
        }

        public RpcChannelConfig build() {
            // Validate configuration
            if (channel == null || channel.isEmpty()) {
                throw new IllegalArgumentException("Channel must not be null or empty");
            }
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be positive");
            }
            if (pattern == null) {
                throw new IllegalArgumentException("Pattern must not be null");
            }

            return new RpcChannelConfig(this);
        }
    }

    /**
     * Create IPC (Inter-Process Communication) channel configuration.
     *
     * @param streamId stream ID
     * @param pattern communication pattern
     * @return channel configuration
     */
    public static RpcChannelConfig createIpc(int streamId, RpcPattern pattern) {
        return builder()
            .channel("aeron:ipc")
            .streamId(streamId)
            .pattern(pattern)
            .build();
    }

    /**
     * Create UDP unicast channel configuration.
     *
     * @param host host address
     * @param port port number
     * @param streamId stream ID
     * @param pattern communication pattern
     * @return channel configuration
     */
    public static RpcChannelConfig createUnicast(String host, int port, int streamId, RpcPattern pattern) {
        return builder()
            .channel(String.format("aeron:udp?endpoint=%s:%d", host, port))
            .streamId(streamId)
            .pattern(pattern)
            .build();
    }

    /**
     * Create UDP multicast channel configuration.
     *
     * @param group multicast group address
     * @param port port number
     * @param streamId stream ID
     * @param pattern communication pattern
     * @return channel configuration
     */
    public static RpcChannelConfig createMulticast(String group, int port, int streamId, RpcPattern pattern) {
        return builder()
            .channel(String.format("aeron:udp?endpoint=%s:%d|interface=localhost", group, port))
            .streamId(streamId)
            .pattern(pattern)
            .build();
    }
}
