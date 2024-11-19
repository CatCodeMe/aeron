package io.aeron.rpc.config;

/**
 * Handle for a configuration watch registration.
 * Used to cancel the watch when it's no longer needed.
 */
public interface ConfigurationWatch extends AutoCloseable {
    /**
     * Check if this watch is still active.
     *
     * @return true if the watch is active
     */
    boolean isActive();

    /**
     * Cancel this watch.
     */
    void cancel();

    /**
     * Get the key or prefix being watched.
     *
     * @return Watched key or prefix
     */
    String getWatchedKey();

    /**
     * Get the associated configuration listener.
     *
     * @return Configuration listener
     */
    ConfigurationListener getListener();

    @Override
    default void close() {
        cancel();
    }
}
