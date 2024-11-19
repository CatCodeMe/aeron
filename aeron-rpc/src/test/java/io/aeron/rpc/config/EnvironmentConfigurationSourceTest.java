package io.aeron.rpc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnvironmentConfigurationSourceTest {
    private EnvironmentConfigurationSource source;

    @BeforeEach
    void setUp() {
        source = new EnvironmentConfigurationSource();
    }

    @Test
    void getValue_ExistingEnvironmentVariable_ReturnsValue() {
        // Get an environment variable that should exist on all systems
        String path = System.getenv("PATH");
        assertNotNull(path);

        Optional<String> value = source.getValue("PATH").join();
        assertTrue(value.isPresent());
        assertEquals(path, value.get());
    }

    @Test
    void getValue_NonExistentVariable_ReturnsEmpty() {
        Optional<String> value = source.getValue("NONEXISTENT_TEST_VAR").join();
        assertFalse(value.isPresent());
    }

    @Test
    void getValuesWithPrefix_ExistingPrefix_ReturnsMatchingValues() {
        // Most systems have multiple PATH-related environment variables
        Map<String, String> values = source.getValuesWithPrefix("PATH").join();
        assertFalse(values.isEmpty());
        assertTrue(values.containsKey("PATH"));
    }

    @Test
    void getValuesWithPrefix_NonExistentPrefix_ReturnsEmptyMap() {
        Map<String, String> values = source.getValuesWithPrefix("NONEXISTENT_PREFIX_").join();
        assertTrue(values.isEmpty());
    }

    @Test
    void watch_EnvironmentVariableChange_NoNotification() {
        boolean[] listenerCalled = {false};
        ConfigurationWatch watch = source.watch("TEST_VAR", event -> listenerCalled[0] = true);

        // Environment variables can't be changed at runtime, so listener should never be called
        assertFalse(listenerCalled[0]);
        watch.cancel();
    }

    @Test
    void watchPrefix_EnvironmentVariableChange_NoNotification() {
        boolean[] listenerCalled = {false};
        ConfigurationWatch watch = source.watchPrefix("TEST_", event -> listenerCalled[0] = true);

        // Environment variables can't be changed at runtime, so listener should never be called
        assertFalse(listenerCalled[0]);
        watch.cancel();
    }

    @Test
    void getName_ReturnsCorrectName() {
        assertEquals("environment", source.getName());
    }

    @Test
    void getPriority_ReturnsDefaultPriority() {
        assertEquals(200, source.getPriority());
    }

    @Test
    void getPriority_CustomPriority_ReturnsCustomPriority() {
        EnvironmentConfigurationSource customSource = new EnvironmentConfigurationSource(300);
        assertEquals(300, customSource.getPriority());
    }

    @Test
    void watch_CancelWatch_WatchBecomesInactive() {
        ConfigurationWatch watch = source.watch("TEST_VAR", event -> {});
        assertTrue(watch.isActive());

        watch.cancel();
        assertFalse(watch.isActive());
    }

    @Test
    void watch_GetWatchedKey_ReturnsCorrectKey() {
        String key = "TEST_VAR";
        ConfigurationWatch watch = source.watch(key, event -> {});
        assertEquals(key, watch.getWatchedKey());
        watch.cancel();
    }

    @Test
    void watch_GetListener_ReturnsCorrectListener() {
        ConfigurationListener listener = event -> {};
        ConfigurationWatch watch = source.watch("TEST_VAR", listener);
        assertEquals(listener, watch.getListener());
        watch.cancel();
    }
}
