package io.aeron.rpc.security;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Authentication provider interface for RPC security.
 * Handles user authentication and token validation.
 */
public interface AuthenticationProvider {
    /**
     * Authenticate a user with credentials.
     *
     * @param credentials Map of credential key-value pairs
     * @return Future containing authentication token if successful
     */
    CompletableFuture<AuthToken> authenticate(Map<String, String> credentials);

    /**
     * Validate an authentication token.
     *
     * @param token Authentication token to validate
     * @return Future containing validation result
     */
    CompletableFuture<Boolean> validateToken(AuthToken token);

    /**
     * Revoke an authentication token.
     *
     * @param token Token to revoke
     * @return Future completing when token is revoked
     */
    CompletableFuture<Void> revokeToken(AuthToken token);

    /**
     * Refresh an authentication token.
     *
     * @param token Token to refresh
     * @return Future containing new token
     */
    CompletableFuture<AuthToken> refreshToken(AuthToken token);
}
