package io.aeron.rpc.security;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Authentication token for RPC security.
 * Contains token value, expiration, and claims.
 */
public class AuthToken {
    private final String tokenValue;
    private final Instant expiration;
    private final Map<String, Object> claims;

    private AuthToken(Builder builder) {
        this.tokenValue = Objects.requireNonNull(builder.tokenValue, "Token value must not be null");
        this.expiration = Objects.requireNonNull(builder.expiration, "Expiration must not be null");
        this.claims = Collections.unmodifiableMap(new HashMap<>(builder.claims));
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public Instant getExpiration() {
        return expiration;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiration);
    }

    public Object getClaim(String key) {
        return claims.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tokenValue;
        private Instant expiration;
        private final Map<String, Object> claims = new HashMap<>();

        public Builder withTokenValue(String value) {
            this.tokenValue = value;
            return this;
        }

        public Builder withExpiration(Instant exp) {
            this.expiration = exp;
            return this;
        }

        public Builder withClaim(String key, Object value) {
            this.claims.put(key, value);
            return this;
        }

        public Builder withClaims(Map<String, Object> claims) {
            this.claims.putAll(claims);
            return this;
        }

        public AuthToken build() {
            return new AuthToken(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthToken authToken = (AuthToken) o;
        return Objects.equals(tokenValue, authToken.tokenValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenValue);
    }
}
