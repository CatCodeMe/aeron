package io.aeron.rpc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * File-based configuration source implementation.
 * Supports properties files and automatic reload on file changes.
 */
public class FileConfigurationSource implements ConfigurationSource {
    private static final Logger logger = LoggerFactory.getLogger(FileConfigurationSource.class);
    private static final int DEFAULT_PRIORITY = 100;

    private final Path configFile;
    private final Properties properties;
    private final Map<String, Set<ConfigurationWatch>> watches;
    private final ScheduledExecutorService watchService;
    private final AtomicBoolean running;
    private final int priority;

    public FileConfigurationSource(Path configFile) {
        this(configFile, DEFAULT_PRIORITY);
    }

    public FileConfigurationSource(Path configFile, int priority) {
        this.configFile = configFile;
        this.properties = new Properties();
        this.watches = new ConcurrentHashMap<>();
        this.watchService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "config-watch-" + configFile.getFileName());
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(true);
        this.priority = priority;

        loadProperties();
        startFileWatcher();
    }

    private void loadProperties() {
        try {
            properties.load(Files.newBufferedReader(configFile));
        } catch (IOException e) {
            logger.error("Failed to load properties from file: {}", configFile, e);
        }
    }

    private void startFileWatcher() {
        watchService.scheduleWithFixedDelay(() -> {
            try {
                Properties newProps = new Properties();
                newProps.load(Files.newBufferedReader(configFile));

                // Find changes
                Set<String> changedKeys = new HashSet<>();
                for (String key : properties.stringPropertyNames()) {
                    String oldValue = properties.getProperty(key);
                    String newValue = newProps.getProperty(key);
                    if (!Objects.equals(oldValue, newValue)) {
                        changedKeys.add(key);
                        notifyWatchers(key, oldValue, newValue);
                    }
                }

                // Find new keys
                for (String key : newProps.stringPropertyNames()) {
                    if (!properties.containsKey(key)) {
                        changedKeys.add(key);
                        notifyWatchers(key, null, newProps.getProperty(key));
                    }
                }

                if (!changedKeys.isEmpty()) {
                    properties.clear();
                    properties.putAll(newProps);
                    logger.info("Configuration reloaded, {} keys changed", changedKeys.size());
                }
            } catch (IOException e) {
                logger.error("Failed to reload properties from file: {}", configFile, e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void notifyWatchers(String key, String oldValue, String newValue) {
        // Notify exact key watchers
        notifyKeyWatchers(key, oldValue, newValue);

        // Notify prefix watchers
        for (String watchedPrefix : watches.keySet()) {
            if (key.startsWith(watchedPrefix)) {
                notifyKeyWatchers(watchedPrefix, oldValue, newValue);
            }
        }
    }

    private void notifyKeyWatchers(String key, String oldValue, String newValue) {
        Set<ConfigurationWatch> keyWatches = watches.get(key);
        if (keyWatches != null) {
            ConfigurationEvent event;
            if (oldValue == null) {
                event = ConfigurationEvent.added(key, newValue, getName());
            } else if (newValue == null) {
                event = ConfigurationEvent.deleted(key, oldValue, getName());
            } else {
                event = ConfigurationEvent.updated(key, oldValue, newValue, getName());
            }

            for (ConfigurationWatch watch : keyWatches) {
                try {
                    watch.getListener().onConfigurationChange(event);
                } catch (Exception e) {
                    logger.error("Error notifying configuration listener", e);
                }
            }
        }
    }

    @Override
    public CompletableFuture<Optional<String>> getValue(String key) {
        return CompletableFuture.completedFuture(
            Optional.ofNullable(properties.getProperty(key))
        );
    }

    @Override
    public CompletableFuture<Map<String, String>> getValuesWithPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                result.put(key, properties.getProperty(key));
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public ConfigurationWatch watch(String key, ConfigurationListener listener) {
        FileConfigurationWatch watch = new FileConfigurationWatch(key, listener);
        watches.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(watch);
        return watch;
    }

    @Override
    public ConfigurationWatch watchPrefix(String prefix, ConfigurationListener listener) {
        return watch(prefix, listener);
    }

    @Override
    public String getName() {
        return "file:" + configFile.getFileName();
    }

    @Override
    public int getPriority() {
        return priority;
    }

    /**
     * Stop watching for configuration changes.
     */
    public void close() {
        if (running.compareAndSet(true, false)) {
            watchService.shutdown();
            try {
                watchService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private class FileConfigurationWatch implements ConfigurationWatch {
        private final String key;
        private final ConfigurationListener listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        FileConfigurationWatch(String key, ConfigurationListener listener) {
            this.key = key;
            this.listener = listener;
        }

        @Override
        public boolean isActive() {
            return active.get() && running.get();
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
