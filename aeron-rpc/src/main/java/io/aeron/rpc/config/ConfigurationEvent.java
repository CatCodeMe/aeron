package io.aeron.rpc.config;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Event object for configuration changes.
 */
public class ConfigurationEvent {
    private final String key;
    private final Optional<String> oldValue;
    private final Optional<String> newValue;
    private final String source;
    private final Instant timestamp;
    private final Type type;

    public ConfigurationEvent(String key, Optional<String> oldValue, Optional<String> newValue,
                            String source, Type type) {
        this.key = Objects.requireNonNull(key, "Key must not be null");
        this.oldValue = Objects.requireNonNull(oldValue, "Old value must not be null");
        this.newValue = Objects.requireNonNull(newValue, "New value must not be null");
        this.source = Objects.requireNonNull(source, "Source must not be null");
        this.type = Objects.requireNonNull(type, "Type must not be null");
        this.timestamp = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public Optional<String> getOldValue() {
        return oldValue;
    }

    public Optional<String> getNewValue() {
        return newValue;
    }

    public String getSource() {
        return source;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Type getType() {
        return type;
    }

    public boolean isAdd() {
        return type == Type.ADD;
    }

    public boolean isUpdate() {
        return type == Type.UPDATE;
    }

    public boolean isDelete() {
        return type == Type.DELETE;
    }

    @Override
    public String toString() {
        return String.format("ConfigurationEvent{key='%s', oldValue=%s, newValue=%s, source='%s', type=%s, timestamp=%s}",
                key, oldValue, newValue, source, type, timestamp);
    }

    /**
     * Type of configuration change event.
     */
    public enum Type {
        /** New configuration value added */
        ADD,
        /** Existing configuration value updated */
        UPDATE,
        /** Configuration value deleted */
        DELETE
    }

    /**
     * Create an event for a new configuration value.
     */
    public static ConfigurationEvent added(String key, String value, String source) {
        return new ConfigurationEvent(key, Optional.empty(), Optional.of(value), source, Type.ADD);
    }

    /**
     * Create an event for an updated configuration value.
     */
    public static ConfigurationEvent updated(String key, String oldValue, String newValue, String source) {
        return new ConfigurationEvent(key, Optional.of(oldValue), Optional.of(newValue), source, Type.UPDATE);
    }

    /**
     * Create an event for a deleted configuration value.
     */
    public static ConfigurationEvent deleted(String key, String oldValue, String source) {
        return new ConfigurationEvent(key, Optional.of(oldValue), Optional.empty(), source, Type.DELETE);
    }
}
