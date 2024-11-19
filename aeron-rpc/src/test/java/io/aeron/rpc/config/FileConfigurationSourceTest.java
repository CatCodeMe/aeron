package io.aeron.rpc.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FileConfigurationSourceTest {
    @TempDir
    Path tempDir;

    private Path configFile;
    private FileConfigurationSource source;

    @BeforeEach
    void setUp() throws Exception {
        configFile = tempDir.resolve("test.properties");
        Properties props = new Properties();
        props.setProperty("test.key", "test.value");
        props.setProperty("prefix.key1", "value1");
        props.setProperty("prefix.key2", "value2");
        props.store(Files.newBufferedWriter(configFile), "Test properties");

        source = new FileConfigurationSource(configFile);
    }

    @AfterEach
    void tearDown() {
        source.close();
    }

    @Test
    void getValue_ExistingKey_ReturnsValue() {
        Optional<String> value = source.getValue("test.key").join();
        assertTrue(value.isPresent());
        assertEquals("test.value", value.get());
    }

    @Test
    void getValue_NonExistentKey_ReturnsEmpty() {
        Optional<String> value = source.getValue("nonexistent.key").join();
        assertFalse(value.isPresent());
    }

    @Test
    void getValuesWithPrefix_ExistingPrefix_ReturnsMatchingValues() {
        Map<String, String> values = source.getValuesWithPrefix("prefix.").join();
        assertEquals(2, values.size());
        assertEquals("value1", values.get("prefix.key1"));
        assertEquals("value2", values.get("prefix.key2"));
    }

    @Test
    void getValuesWithPrefix_NonExistentPrefix_ReturnsEmptyMap() {
        Map<String, String> values = source.getValuesWithPrefix("nonexistent.").join();
        assertTrue(values.isEmpty());
    }

    @Test
    void watch_ValueChanged_NotifiesListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConfigurationEvent> eventRef = new AtomicReference<>();

        ConfigurationWatch watch = source.watch("test.key", event -> {
            eventRef.set(event);
            latch.countDown();
        });

        // Modify the file
        Properties props = new Properties();
        props.setProperty("test.key", "new.value");
        props.store(Files.newBufferedWriter(configFile), "Updated properties");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        ConfigurationEvent event = eventRef.get();
        assertNotNull(event);
        assertEquals("test.key", event.getKey());
        assertEquals("test.value", event.getOldValue());
        assertEquals("new.value", event.getNewValue());
        assertEquals(ConfigurationEvent.Type.UPDATED, event.getType());

        watch.cancel();
    }

    @Test
    void watch_ValueDeleted_NotifiesListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConfigurationEvent> eventRef = new AtomicReference<>();

        ConfigurationWatch watch = source.watch("test.key", event -> {
            eventRef.set(event);
            latch.countDown();
        });

        // Delete the key
        Properties props = new Properties();
        props.store(Files.newBufferedWriter(configFile), "Empty properties");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        ConfigurationEvent event = eventRef.get();
        assertNotNull(event);
        assertEquals("test.key", event.getKey());
        assertEquals("test.value", event.getOldValue());
        assertNull(event.getNewValue());
        assertEquals(ConfigurationEvent.Type.DELETED, event.getType());

        watch.cancel();
    }

    @Test
    void watch_ValueAdded_NotifiesListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConfigurationEvent> eventRef = new AtomicReference<>();

        ConfigurationWatch watch = source.watch("new.key", event -> {
            eventRef.set(event);
            latch.countDown();
        });

        // Add new key
        Properties props = new Properties();
        props.load(Files.newBufferedReader(configFile));
        props.setProperty("new.key", "new.value");
        props.store(Files.newBufferedWriter(configFile), "Added new key");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        ConfigurationEvent event = eventRef.get();
        assertNotNull(event);
        assertEquals("new.key", event.getKey());
        assertNull(event.getOldValue());
        assertEquals("new.value", event.getNewValue());
        assertEquals(ConfigurationEvent.Type.ADDED, event.getType());

        watch.cancel();
    }

    @Test
    void watchPrefix_ValueChanged_NotifiesListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConfigurationEvent> eventRef = new AtomicReference<>();

        ConfigurationWatch watch = source.watchPrefix("prefix.", event -> {
            eventRef.set(event);
            latch.countDown();
        });

        // Modify the file
        Properties props = new Properties();
        props.load(Files.newBufferedReader(configFile));
        props.setProperty("prefix.key1", "new.value1");
        props.store(Files.newBufferedWriter(configFile), "Updated properties");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        ConfigurationEvent event = eventRef.get();
        assertNotNull(event);
        assertEquals("prefix.key1", event.getKey());
        assertEquals("value1", event.getOldValue());
        assertEquals("new.value1", event.getNewValue());
        assertEquals(ConfigurationEvent.Type.UPDATED, event.getType());

        watch.cancel();
    }

    @Test
    void getName_ReturnsCorrectName() {
        assertEquals("file:" + configFile.getFileName(), source.getName());
    }

    @Test
    void getPriority_ReturnsDefaultPriority() {
        assertEquals(100, source.getPriority());
    }

    @Test
    void getPriority_CustomPriority_ReturnsCustomPriority() {
        FileConfigurationSource customSource = new FileConfigurationSource(configFile, 200);
        assertEquals(200, customSource.getPriority());
        customSource.close();
    }
}
