# Configuration Management

The Aeron RPC framework provides a flexible and extensible configuration management system that supports multiple configuration sources, dynamic updates, and priority-based configuration resolution.

## Overview

The configuration system consists of three main components:

1. Configuration Sources
2. Configuration Manager
3. Configuration Events and Watches

### Key Features

- Multiple configuration sources with priority ordering
- Dynamic configuration updates
- Asynchronous configuration access
- Hierarchical configuration support
- Thread-safe implementation
- Resource-efficient design

## Configuration Sources

### File Configuration Source

The `FileConfigurationSource` provides configuration from properties files with automatic reload capability.

```java
Path configFile = Paths.get("config.properties");
ConfigurationSource fileSource = new FileConfigurationSource(configFile);
```

Features:
- Automatic file change detection
- Properties file format support
- Configurable reload interval
- Default priority: 100

### Environment Configuration Source

The `EnvironmentConfigurationSource` provides access to system environment variables.

```java
ConfigurationSource envSource = new EnvironmentConfigurationSource();
```

Features:
- System environment variable access
- Higher default priority (200)
- Read-only configuration

## Configuration Manager

The `ConfigurationManager` provides unified access to all configuration sources:

```java
ConfigurationManager manager = new ConfigurationManager();
manager.addSource(new EnvironmentConfigurationSource());
manager.addSource(new FileConfigurationSource(configFile));

// Get configuration value
CompletableFuture<Optional<String>> value = manager.getValue("app.name");

// Get values with prefix
CompletableFuture<Map<String, String>> dbConfig = 
    manager.getValuesWithPrefix("database.");
```

### Priority Resolution

Configuration values are resolved based on source priority:
1. Higher priority sources override lower priority sources
2. First matching value from highest priority source is used
3. Empty optional returned if no source has the requested key

## Configuration Watches

The configuration system supports watching for configuration changes:

```java
ConfigurationWatch watch = manager.watch("app.name", event -> {
    System.out.println("Configuration changed: " + event);
});

// Later, cancel the watch
watch.cancel();
```

### Watch Types

1. **Key Watch**: Watch specific configuration key
2. **Prefix Watch**: Watch all keys with given prefix

## Best Practices

1. **Source Priority**
   - Use environment variables for overrides (highest priority)
   - Use file-based config for default values
   - Add custom sources with appropriate priorities

2. **Resource Management**
   ```java
   try (ConfigurationManager manager = new ConfigurationManager()) {
       manager.addSource(new EnvironmentConfigurationSource());
       // Use manager
   }
   ```

3. **Error Handling**
   - Handle missing configuration gracefully
   - Provide default values where appropriate
   - Log configuration changes at appropriate level

4. **Performance**
   - Cache frequently accessed values
   - Use prefix watches for related configurations
   - Clean up unused watches

## Example Usage

### Basic Configuration

```java
ConfigurationManager manager = new ConfigurationManager();
manager.addSource(new EnvironmentConfigurationSource());
manager.addSource(new FileConfigurationSource(Paths.get("app.properties")));

// Get configuration value
String name = manager.getValue("app.name").join().orElse("default");
```

### Dynamic Configuration

```java
// Watch for changes
manager.watch("app.name", event -> {
    switch (event.getType()) {
        case ADDED:
            System.out.println("New config: " + event.getNewValue());
            break;
        case UPDATED:
            System.out.println("Updated config: " + 
                event.getOldValue() + " -> " + event.getNewValue());
            break;
        case DELETED:
            System.out.println("Deleted config: " + event.getOldValue());
            break;
    }
});
```

### Prefix-based Configuration

```java
// Get all database configuration
Map<String, String> dbConfig = manager.getValuesWithPrefix("database.").join();

// Watch all database configuration changes
manager.watchPrefix("database.", event -> {
    System.out.println("Database config changed: " + event);
});
```

## Integration with Spring

The configuration system can be integrated with Spring:

```java
@Configuration
public class AppConfig {
    @Bean
    public ConfigurationManager configurationManager() {
        ConfigurationManager manager = new ConfigurationManager();
        manager.addSource(new EnvironmentConfigurationSource());
        manager.addSource(new FileConfigurationSource(
            Paths.get("application.properties")));
        return manager;
    }
}
```

## Security Considerations

1. **Sensitive Data**
   - Use environment variables for sensitive data
   - Never log sensitive configuration values
   - Consider encryption for sensitive file-based config

2. **File Permissions**
   - Restrict access to configuration files
   - Use appropriate file ownership
   - Monitor file changes

## Troubleshooting

Common issues and solutions:

1. **Configuration Not Updated**
   - Check file permissions
   - Verify file watch service is running
   - Check for file system limitations

2. **Memory Usage**
   - Clean up unused watches
   - Limit number of watchers
   - Use appropriate watch granularity

3. **Performance Issues**
   - Cache frequently used values
   - Use batch configuration updates
   - Monitor file system events
