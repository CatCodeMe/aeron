package io.aeron.rpc.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Role-based security policy implementation.
 * Manages access control based on user roles and permissions.
 */
public class RoleBasedSecurityPolicy implements SecurityPolicy {
    private static final Logger logger = LoggerFactory.getLogger(RoleBasedSecurityPolicy.class);

    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> serviceOperations = new ConcurrentHashMap<>();
    private final Map<String, SecurityLevel> serviceSecurityLevels = new ConcurrentHashMap<>();

    public RoleBasedSecurityPolicy() {
        initializeDefaultSecurityLevels();
    }

    private void initializeDefaultSecurityLevels() {
        // Default security levels for different types of services
        serviceSecurityLevels.put("admin", SecurityLevel.HIGH);
        serviceSecurityLevels.put("user", SecurityLevel.MEDIUM);
        serviceSecurityLevels.put("public", SecurityLevel.LOW);
    }

    public void addRolePermission(String role, String permission) {
        rolePermissions.computeIfAbsent(role, k -> ConcurrentHashMap.newKeySet()).add(permission);
    }

    public void addServiceOperation(String service, String operation) {
        serviceOperations.computeIfAbsent(service, k -> ConcurrentHashMap.newKeySet()).add(operation);
    }

    public void setServiceSecurityLevel(String service, SecurityLevel level) {
        serviceSecurityLevels.put(service, level);
    }

    @Override
    public CompletableFuture<Boolean> hasServiceAccess(AuthToken token, String service) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String role = (String) token.getClaim("role");
                if (role == null) {
                    return false;
                }

                Set<String> permissions = rolePermissions.get(role);
                return permissions != null && permissions.contains("service:" + service);
            } catch (Exception e) {
                logger.error("Service access check failed", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> hasOperationPermission(AuthToken token, String service, String operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String role = (String) token.getClaim("role");
                if (role == null) {
                    return false;
                }

                Set<String> permissions = rolePermissions.get(role);
                return permissions != null && 
                       (permissions.contains("service:" + service + ":*") ||
                        permissions.contains("service:" + service + ":" + operation));
            } catch (Exception e) {
                logger.error("Operation permission check failed", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Set<String>> getAccessibleServices(AuthToken token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String role = (String) token.getClaim("role");
                if (role == null) {
                    return Collections.emptySet();
                }

                Set<String> permissions = rolePermissions.get(role);
                if (permissions == null) {
                    return Collections.emptySet();
                }

                Set<String> services = new HashSet<>();
                for (String permission : permissions) {
                    if (permission.startsWith("service:")) {
                        String[] parts = permission.split(":");
                        if (parts.length >= 2) {
                            services.add(parts[1]);
                        }
                    }
                }
                return services;
            } catch (Exception e) {
                logger.error("Accessible services check failed", e);
                return Collections.emptySet();
            }
        });
    }

    @Override
    public CompletableFuture<Set<String>> getPermittedOperations(AuthToken token, String service) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String role = (String) token.getClaim("role");
                if (role == null) {
                    return Collections.emptySet();
                }

                Set<String> permissions = rolePermissions.get(role);
                if (permissions == null) {
                    return Collections.emptySet();
                }

                if (permissions.contains("service:" + service + ":*")) {
                    return serviceOperations.getOrDefault(service, Collections.emptySet());
                }

                Set<String> operations = new HashSet<>();
                for (String permission : permissions) {
                    if (permission.startsWith("service:" + service + ":")) {
                        String[] parts = permission.split(":");
                        if (parts.length >= 3) {
                            operations.add(parts[2]);
                        }
                    }
                }
                return operations;
            } catch (Exception e) {
                logger.error("Permitted operations check failed", e);
                return Collections.emptySet();
            }
        });
    }

    @Override
    public boolean requiresEncryption(String service) {
        SecurityLevel level = serviceSecurityLevels.getOrDefault(service, SecurityLevel.MEDIUM);
        return level.ordinal() >= SecurityLevel.MEDIUM.ordinal();
    }

    @Override
    public boolean requiresAuthentication(String service) {
        SecurityLevel level = serviceSecurityLevels.getOrDefault(service, SecurityLevel.MEDIUM);
        return level.ordinal() >= SecurityLevel.LOW.ordinal();
    }

    public enum SecurityLevel {
        LOW,
        MEDIUM,
        HIGH
    }
}
