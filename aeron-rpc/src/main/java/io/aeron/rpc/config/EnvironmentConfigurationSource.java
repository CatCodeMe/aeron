package io.aeron.rpc.config;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Environment variable based configuration source.
 * Provides access to system environment variables.
 */
public class EnvironmentConfigurationSource implements ConfigurationSource {
    private static final int DEFAULT_PRIORITY = 200;

    private final Map<String, String> environment;
    private final Map<String, Set<ConfigurationWatch>> watches;
    private final int priority;

    public EnvironmentConfigurationSource() {
        this(DEFAULT_PRIORITY);
    }

    public EnvironmentConfigurationSource(int priority) {
        this.environment = new HashMap<>(System.getenv());
        this.watches = new ConcurrentHashMap<>();
        this.priority = priority;
    }

    @Override
    public CompletableFuture<Optional<String>> getValue(String key) {
        return CompletableFuture.completedFuture(Optional.ofNullable(environment.get(key)));
    }

    @Override
    public CompletableFuture<Map<String, String>> getValuesWithPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public ConfigurationWatch watch(String key, ConfigurationListener listener) {
        EnvironmentConfigurationWatch watch = new EnvironmentConfigurationWatch(key, listener);
        watches.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(watch);
        return watch;
    }

    @Override
    public ConfigurationWatch watchPrefix(String prefix, ConfigurationListener listener) {
        return watch(prefix, listener);
    }

    @Override
    public String getName() {
        return "environment";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    private class EnvironmentConfigurationWatch implements ConfigurationWatch {
        private final String key;
        private final ConfigurationListener listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        EnvironmentConfigurationWatch(String key, ConfigurationListener listener) {
            this.key = key;
            this.listener = listener;
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void cancel() {
            if (active.compareAndSet(true, false)) {
                watches.get(key).remove(this);
            }
        }

        @Override
        public String getWatchedKey() {
            return key;
        }

        @Override
        public ConfigurationListener getListener() {
            return listener;
        }
    }
}
