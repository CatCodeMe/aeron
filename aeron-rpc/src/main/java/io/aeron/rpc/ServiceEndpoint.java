package io.aeron.rpc;

import java.util.Objects;

/**
 * Represents a service endpoint with connection details.
 */
public class ServiceEndpoint {
    private final String host;
    private final int port;
    private final String channel;
    private final int streamId;
    private final String version;

    public ServiceEndpoint(String host, int port, String channel, int streamId, String version) {
        this.host = Objects.requireNonNull(host, "host must not be null");
        this.port = port;
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.streamId = streamId;
        this.version = Objects.requireNonNull(version, "version must not be null");
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getChannel() {
        return channel;
    }

    public int getStreamId() {
        return streamId;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceEndpoint that = (ServiceEndpoint) o;
        return port == that.port &&
               streamId == that.streamId &&
               Objects.equals(host, that.host) &&
               Objects.equals(channel, that.channel) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, channel, streamId, version);
    }

    @Override
    public String toString() {
        return "ServiceEndpoint{" +
               "host='" + host + '\'' +
               ", port=" + port +
               ", channel='" + channel + '\'' +
               ", streamId=" + streamId +
               ", version='" + version + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host;
        private int port;
        private String channel;
        private int streamId;
        private String version;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder streamId(int streamId) {
            this.streamId = streamId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public ServiceEndpoint build() {
            return new ServiceEndpoint(host, port, channel, streamId, version);
        }
    }
}
