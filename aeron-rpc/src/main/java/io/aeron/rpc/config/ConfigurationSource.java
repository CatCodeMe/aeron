package io.aeron.rpc.config;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Configuration source interface.
 * Provides access to configuration properties from various sources.
 */
public interface ConfigurationSource {
    /**
     * Get a configuration value by key.
     *
     * @param key Configuration key
     * @return Optional containing the value if present
     */
    CompletableFuture<Optional<String>> getValue(String key);

    /**
     * Get all configuration values with a specific prefix.
     *
     * @param prefix Configuration key prefix
     * @return Map of matching configuration key-value pairs
     */
    CompletableFuture<Map<String, String>> getValuesWithPrefix(String prefix);

    /**
     * Watch for changes to a specific configuration key.
     *
     * @param key Configuration key to watch
     * @param listener Listener to notify of changes
     * @return Registration handle for the watch
     */
    ConfigurationWatch watch(String key, ConfigurationListener listener);

    /**
     * Watch for changes to configuration values with a specific prefix.
     *
     * @param prefix Configuration key prefix to watch
     * @param listener Listener to notify of changes
     * @return Registration handle for the watch
     */
    ConfigurationWatch watchPrefix(String prefix, ConfigurationListener listener);

    /**
     * Get the name of this configuration source.
     *
     * @return Configuration source name
     */
    String getName();

    /**
     * Get the priority of this configuration source.
     * Higher priority sources override lower priority ones.
     *
     * @return Priority value
     */
    int getPriority();
}
