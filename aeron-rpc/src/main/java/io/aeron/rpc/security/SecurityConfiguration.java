package io.aeron.rpc.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security configuration manager for the RPC framework.
 * Handles security settings, authentication, and encryption configurations.
 */
public class SecurityConfiguration {
    private final Map<String, Object> securitySettings;
    private final AuthenticationProvider authProvider;
    private final EncryptionProvider encryptionProvider;
    private final Map<String, Set<String>> servicePermissions;
    private final SecurityPolicy securityPolicy;

    private SecurityConfiguration(Builder builder) {
        this.securitySettings = new ConcurrentHashMap<>(builder.securitySettings);
        this.authProvider = builder.authProvider;
        this.encryptionProvider = builder.encryptionProvider;
        this.servicePermissions = new ConcurrentHashMap<>(builder.servicePermissions);
        this.securityPolicy = builder.securityPolicy;
    }

    public AuthenticationProvider getAuthProvider() {
        return authProvider;
    }

    public EncryptionProvider getEncryptionProvider() {
        return encryptionProvider;
    }

    public SecurityPolicy getSecurityPolicy() {
        return securityPolicy;
    }

    public Set<String> getServicePermissions(String service) {
        return servicePermissions.getOrDefault(service, Collections.emptySet());
    }

    public Object getSecuritySetting(String key) {
        return securitySettings.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Object> securitySettings = new HashMap<>();
        private final Map<String, Set<String>> servicePermissions = new HashMap<>();
        private AuthenticationProvider authProvider;
        private EncryptionProvider encryptionProvider;
        private SecurityPolicy securityPolicy;

        public Builder withAuthenticationProvider(AuthenticationProvider provider) {
            this.authProvider = provider;
            return this;
        }

        public Builder withEncryptionProvider(EncryptionProvider provider) {
            this.encryptionProvider = provider;
            return this;
        }

        public Builder withSecurityPolicy(SecurityPolicy policy) {
            this.securityPolicy = policy;
            return this;
        }

        public Builder withSecuritySetting(String key, Object value) {
            this.securitySettings.put(key, value);
            return this;
        }

        public Builder withServicePermissions(String service, Set<String> permissions) {
            this.servicePermissions.put(service, permissions);
            return this;
        }

        public SecurityConfiguration build() {
            if (authProvider == null) {
                throw new IllegalStateException("Authentication provider must be configured");
            }
            if (encryptionProvider == null) {
                throw new IllegalStateException("Encryption provider must be configured");
            }
            if (securityPolicy == null) {
                throw new IllegalStateException("Security policy must be configured");
            }
            return new SecurityConfiguration(this);
        }
    }
}
