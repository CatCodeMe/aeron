package io.aeron.rpc.config;

/**
 * Listener interface for configuration changes.
 */
@FunctionalInterface
public interface ConfigurationListener {
    /**
     * Called when a configuration value changes.
     *
     * @param event Configuration change event
     */
    void onConfigurationChange(ConfigurationEvent event);
}
