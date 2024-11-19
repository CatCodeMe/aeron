package io.aeron.rpc.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationManagerTest {
    @Mock
    private ConfigurationSource highPrioritySource;

    @Mock
    private ConfigurationSource lowPrioritySource;

    private ConfigurationManager manager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(highPrioritySource.getPriority()).thenReturn(200);
        when(highPrioritySource.getName()).thenReturn("high");
        when(lowPrioritySource.getPriority()).thenReturn(100);
        when(lowPrioritySource.getName()).thenReturn("low");

        manager = new ConfigurationManager();
        manager.addSource(lowPrioritySource);
        manager.addSource(highPrioritySource);
    }

    @Test
    void getValue_HighPrioritySourceHasValue_ReturnsHighPriorityValue() {
        String key = "test.key";
        String highValue = "high.value";
        String lowValue = "low.value";

        when(highPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(highValue)));
        when(lowPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(lowValue)));

        Optional<String> value = manager.getValue(key).join();
        assertTrue(value.isPresent());
        assertEquals(highValue, value.get());
    }

    @Test
    void getValue_OnlyLowPrioritySourceHasValue_ReturnsLowPriorityValue() {
        String key = "test.key";
        String lowValue = "low.value";

        when(highPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(lowPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(lowValue)));

        Optional<String> value = manager.getValue(key).join();
        assertTrue(value.isPresent());
        assertEquals(lowValue, value.get());
    }

    @Test
    void getValue_NoSourceHasValue_ReturnsEmpty() {
        String key = "test.key";

        when(highPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(lowPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        Optional<String> value = manager.getValue(key).join();
        assertFalse(value.isPresent());
    }

    @Test
    void getValuesWithPrefix_MergesValuesFromAllSources() {
        String prefix = "test.";
        Map<String, String> highValues = new HashMap<>();
        highValues.put("test.key1", "high1");
        highValues.put("test.key2", "high2");

        Map<String, String> lowValues = new HashMap<>();
        lowValues.put("test.key2", "low2");
        lowValues.put("test.key3", "low3");

        when(highPrioritySource.getValuesWithPrefix(prefix))
            .thenReturn(CompletableFuture.completedFuture(highValues));
        when(lowPrioritySource.getValuesWithPrefix(prefix))
            .thenReturn(CompletableFuture.completedFuture(lowValues));

        Map<String, String> values = manager.getValuesWithPrefix(prefix).join();
        assertEquals(3, values.size());
        assertEquals("high1", values.get("test.key1"));
        assertEquals("high2", values.get("test.key2")); // High priority wins
        assertEquals("low3", values.get("test.key3"));
    }

    @Test
    void watch_HighPriorityChange_NotifiesListener() {
        String key = "test.key";
        ConfigurationEvent[] eventRef = new ConfigurationEvent[1];
        ConfigurationWatch watch = manager.watch(key, event -> eventRef[0] = event);

        // Mock source watches
        when(highPrioritySource.watch(eq(key), any()))
            .thenReturn(mock(ConfigurationWatch.class));
        when(lowPrioritySource.watch(eq(key), any()))
            .thenReturn(mock(ConfigurationWatch.class));

        // Simulate high priority source change
        when(highPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.of("new.value")));

        // Capture the listener and trigger the event
        verify(highPrioritySource).watch(eq(key), argThat(listener -> {
            listener.onConfigurationChange(new ConfigurationEvent(
                key, "old.value", "new.value",
                ConfigurationEvent.Type.UPDATED, "high"
            ));
            return true;
        }));

        assertNotNull(eventRef[0]);
        assertEquals("new.value", eventRef[0].getNewValue());
        assertEquals("high", eventRef[0].getSource());

        watch.cancel();
    }

    @Test
    void watch_LowPriorityChangeWithHighPriorityValue_DoesNotNotifyListener() {
        String key = "test.key";
        boolean[] listenerCalled = {false};
        ConfigurationWatch watch = manager.watch(key, event -> listenerCalled[0] = true);

        // Mock source watches
        when(highPrioritySource.watch(eq(key), any()))
            .thenReturn(mock(ConfigurationWatch.class));
        when(lowPrioritySource.watch(eq(key), any()))
            .thenReturn(mock(ConfigurationWatch.class));

        // Simulate high priority source having a value
        when(highPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.of("high.value")));

        // Capture the low priority listener and trigger the event
        verify(lowPrioritySource).watch(eq(key), argThat(listener -> {
            listener.onConfigurationChange(new ConfigurationEvent(
                key, "old.value", "new.value",
                ConfigurationEvent.Type.UPDATED, "low"
            ));
            return true;
        }));

        assertFalse(listenerCalled[0]);
        watch.cancel();
    }

    @Test
    void removeSource_SourceRemoved_UpdatesPriorityOrder() {
        manager.removeSource(highPrioritySource);

        String key = "test.key";
        String lowValue = "low.value";
        when(lowPrioritySource.getValue(key))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(lowValue)));

        Optional<String> value = manager.getValue(key).join();
        assertTrue(value.isPresent());
        assertEquals(lowValue, value.get());
    }

    @Test
    void close_AllSourcesAndWatchesClosed() {
        ConfigurationWatch watch = manager.watch("test.key", event -> {});
        manager.close();

        assertFalse(watch.isActive());
        assertTrue(manager.getSources().isEmpty());
    }
}
