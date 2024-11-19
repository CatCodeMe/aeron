package io.aeron.rpc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Configuration manager that aggregates multiple configuration sources.
 * Manages configuration priorities and provides unified access to all sources.
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);

    private final List<ConfigurationSource> sources;
    private final Map<String, Set<ConfigurationWatch>> watches;
    private final Map<ConfigurationWatch, List<ConfigurationWatch>> sourceWatches;

    public ConfigurationManager() {
        this.sources = new CopyOnWriteArrayList<>();
        this.watches = new ConcurrentHashMap<>();
        this.sourceWatches = new ConcurrentHashMap<>();
    }

    /**
     * Add a configuration source.
     * Sources are ordered by priority (highest first).
     */
    public void addSource(ConfigurationSource source) {
        sources.add(source);
        sources.sort((s1, s2) -> Integer.compare(s2.getPriority(), s1.getPriority()));
        logger.info("Added configuration source: {} with priority {}", source.getName(), source.getPriority());
    }

    /**
     * Get a configuration value.
     * Returns the value from the highest priority source that has the key.
     */
    public CompletableFuture<Optional<String>> getValue(String key) {
        return CompletableFuture.supplyAsync(() -> {
            for (ConfigurationSource source : sources) {
                Optional<String> value = source.getValue(key).join();
                if (value.isPresent()) {
                    return value;
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Get all configuration values with a specific prefix.
     * Merges values from all sources, with higher priority sources overriding lower ones.
     */
    public CompletableFuture<Map<String, String>> getValuesWithPrefix(String prefix) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> result = new HashMap<>();
            for (ConfigurationSource source : sources) {
                Map<String, String> sourceValues = source.getValuesWithPrefix(prefix).join();
                result.putAll(sourceValues);
            }
            return result;
        });
    }

    /**
     * Watch for changes to a specific configuration key.
     */
    public ConfigurationWatch watch(String key, ConfigurationListener listener) {
        ManagerConfigurationWatch watch = new ManagerConfigurationWatch(key, listener);
        watches.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(watch);

        // Create watches for all sources
        List<ConfigurationWatch> sourceWatchList = sources.stream()
            .map(source -> source.watch(key, event -> handleSourceEvent(event, watch)))
            .collect(Collectors.toList());
        sourceWatches.put(watch, sourceWatchList);

        return watch;
    }

    /**
     * Watch for changes to configuration values with a specific prefix.
     */
    public ConfigurationWatch watchPrefix(String prefix, ConfigurationListener listener) {
        return watch(prefix, listener);
    }

    private void handleSourceEvent(ConfigurationEvent event, ConfigurationWatch watch) {
        // Only notify if the value is from the highest priority source that has it
        boolean isHighestPriority = sources.stream()
            .takeWhile(source -> !source.getName().equals(event.getSource()))
            .noneMatch(source -> source.getValue(event.getKey()).join().isPresent());

        if (isHighestPriority && watch.isActive()) {
            watch.getListener().onConfigurationChange(event);
        }
    }

    /**
     * Get all configuration sources.
     */
    public List<ConfigurationSource> getSources() {
        return Collections.unmodifiableList(sources);
    }

    /**
     * Remove a configuration source.
     */
    public void removeSource(ConfigurationSource source) {
        sources.remove(source);
        logger.info("Removed configuration source: {}", source.getName());
    }

    /**
     * Close all configuration sources and watches.
     */
    public void close() {
        sourceWatches.values().stream()
            .flatMap(List::stream)
            .forEach(ConfigurationWatch::cancel);
        sourceWatches.clear();
        watches.clear();
        sources.clear();
    }

    private class ManagerConfigurationWatch implements ConfigurationWatch {
        private final String key;
        private final ConfigurationListener listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        ManagerConfigurationWatch(String key, ConfigurationListener listener) {
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
                List<ConfigurationWatch> sourceWatchList = sourceWatches.remove(this);
                if (sourceWatchList != null) {
                    sourceWatchList.forEach(ConfigurationWatch::cancel);
                }
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
