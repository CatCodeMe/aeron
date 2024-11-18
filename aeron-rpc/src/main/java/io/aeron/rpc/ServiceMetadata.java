package io.aeron.rpc;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Metadata for RPC services.
 */
public class ServiceMetadata {
    private final String name;
    private final String version;
    private final Set<String> methods;
    private final Map<String, String> properties;
    private final RpcPattern pattern;

    private ServiceMetadata(Builder builder) {
        this.name = builder.name;
        this.version = builder.version;
        this.methods = Collections.unmodifiableSet(builder.methods);
        this.properties = Collections.unmodifiableMap(builder.properties);
        this.pattern = builder.pattern;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Set<String> getMethods() {
        return methods;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public RpcPattern getPattern() {
        return pattern;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "";
        private String version = "1.0.0";
        private Set<String> methods = Collections.emptySet();
        private Map<String, String> properties = Collections.emptyMap();
        private RpcPattern pattern = RpcPattern.ONE_TO_ONE;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder methods(Set<String> methods) {
            this.methods = methods;
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        public Builder pattern(RpcPattern pattern) {
            this.pattern = pattern;
            return this;
        }

        public ServiceMetadata build() {
            return new ServiceMetadata(this);
        }
    }
}
