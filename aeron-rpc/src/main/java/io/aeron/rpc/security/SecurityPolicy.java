package io.aeron.rpc.security;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Security policy interface for RPC framework.
 * Defines access control and security rules.
 */
public interface SecurityPolicy {
    /**
     * Check if a user has permission to access a service.
     *
     * @param token Authentication token
     * @param service Service name
     * @return Future containing access check result
     */
    CompletableFuture<Boolean> hasServiceAccess(AuthToken token, String service);

    /**
     * Check if a user has permission to perform an operation.
     *
     * @param token Authentication token
     * @param service Service name
     * @param operation Operation name
     * @return Future containing operation check result
     */
    CompletableFuture<Boolean> hasOperationPermission(AuthToken token, String service, String operation);

    /**
     * Get all services accessible to a user.
     *
     * @param token Authentication token
     * @return Future containing set of accessible service names
     */
    CompletableFuture<Set<String>> getAccessibleServices(AuthToken token);

    /**
     * Get all operations permitted for a user on a service.
     *
     * @param token Authentication token
     * @param service Service name
     * @return Future containing set of permitted operation names
     */
    CompletableFuture<Set<String>> getPermittedOperations(AuthToken token, String service);

    /**
     * Check if encryption is required for a service.
     *
     * @param service Service name
     * @return True if encryption is required
     */
    boolean requiresEncryption(String service);

    /**
     * Check if authentication is required for a service.
     *
     * @param service Service name
     * @return True if authentication is required
     */
    boolean requiresAuthentication(String service);
}
