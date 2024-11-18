package io.aeron.rpc.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aeron.rpc.ServiceEndpoint;
import io.aeron.rpc.ServiceMetadata;
import io.aeron.rpc.ServiceRegistry;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ZooKeeper-based service registry implementation.
 */
public class ZookeeperServiceRegistry implements ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperServiceRegistry.class);
    private static final String ROOT_PATH = "/rpc/services";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CuratorFramework client;
    private final Map<String, PathChildrenCache> watchers = new ConcurrentHashMap<>();
    private final Map<String, Set<Consumer<List<ServiceEndpoint>>>> listeners = new ConcurrentHashMap<>();

    public ZookeeperServiceRegistry(String connectionString) {
        this.client = CuratorFrameworkFactory.builder()
            .connectString(connectionString)
            .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            .build();
        this.client.start();

        try {
            // Ensure root path exists
            if (client.checkExists().forPath(ROOT_PATH) == null) {
                client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(ROOT_PATH);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ZooKeeper paths", e);
        }
    }

    @Override
    public void register(String serviceName, ServiceEndpoint endpoint, ServiceMetadata metadata) {
        String path = getServicePath(serviceName, endpoint);
        try {
            byte[] data = MAPPER.writeValueAsBytes(new ServiceRegistration(endpoint, metadata));
            client.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(path, data);
            
            logger.info("Registered service: {} at {}", serviceName, path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register service: " + serviceName, e);
        }
    }

    @Override
    public void unregister(String serviceName, ServiceEndpoint endpoint) {
        String path = getServicePath(serviceName, endpoint);
        try {
            client.delete().forPath(path);
            logger.info("Unregistered service: {} from {}", serviceName, path);
        } catch (Exception e) {
            logger.warn("Failed to unregister service: " + serviceName, e);
        }
    }

    @Override
    public List<ServiceEndpoint> discover(String serviceName) {
        try {
            String servicePath = ROOT_PATH + "/" + serviceName;
            if (client.checkExists().forPath(servicePath) == null) {
                return Collections.emptyList();
            }

            List<String> children = client.getChildren().forPath(servicePath);
            List<ServiceEndpoint> endpoints = new ArrayList<>();

            for (String child : children) {
                byte[] data = client.getData().forPath(servicePath + "/" + child);
                ServiceRegistration registration = MAPPER.readValue(data, ServiceRegistration.class);
                endpoints.add(registration.endpoint);
            }

            return endpoints;
        } catch (Exception e) {
            throw new RuntimeException("Failed to discover service: " + serviceName, e);
        }
    }

    @Override
    public void watch(String serviceName, Consumer<List<ServiceEndpoint>> listener) {
        listeners.computeIfAbsent(serviceName, k -> ConcurrentHashMap.newKeySet()).add(listener);

        if (!watchers.containsKey(serviceName)) {
            try {
                String servicePath = ROOT_PATH + "/" + serviceName;
                PathChildrenCache watcher = new PathChildrenCache(client, servicePath, true);
                
                watcher.getListenable().addListener((client, event) -> {
                    if (event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED ||
                        event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED ||
                        event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {
                        
                        List<ServiceEndpoint> endpoints = discover(serviceName);
                        notifyListeners(serviceName, endpoints);
                    }
                });

                watcher.start();
                watchers.put(serviceName, watcher);
                
                // Initial discovery
                List<ServiceEndpoint> endpoints = discover(serviceName);
                notifyListeners(serviceName, endpoints);
            } catch (Exception e) {
                throw new RuntimeException("Failed to watch service: " + serviceName, e);
            }
        }
    }

    @Override
    public void unwatch(String serviceName, Consumer<List<ServiceEndpoint>> listener) {
        Set<Consumer<List<ServiceEndpoint>>> serviceListeners = listeners.get(serviceName);
        if (serviceListeners != null) {
            serviceListeners.remove(listener);
            if (serviceListeners.isEmpty()) {
                listeners.remove(serviceName);
                PathChildrenCache watcher = watchers.remove(serviceName);
                if (watcher != null) {
                    try {
                        watcher.close();
                    } catch (Exception e) {
                        logger.warn("Failed to close watcher for service: " + serviceName, e);
                    }
                }
            }
        }
    }

    private void notifyListeners(String serviceName, List<ServiceEndpoint> endpoints) {
        Set<Consumer<List<ServiceEndpoint>>> serviceListeners = listeners.get(serviceName);
        if (serviceListeners != null) {
            serviceListeners.forEach(listener -> {
                try {
                    listener.accept(endpoints);
                } catch (Exception e) {
                    logger.error("Failed to notify listener for service: " + serviceName, e);
                }
            });
        }
    }

    private String getServicePath(String serviceName, ServiceEndpoint endpoint) {
        return String.format("%s/%s/%s:%d", ROOT_PATH, serviceName, endpoint.getHost(), endpoint.getPort());
    }

    @Override
    public void close() {
        watchers.values().forEach(watcher -> {
            try {
                watcher.close();
            } catch (Exception e) {
                logger.warn("Failed to close watcher", e);
            }
        });
        watchers.clear();
        listeners.clear();
        client.close();
    }

    private static class ServiceRegistration {
        public ServiceEndpoint endpoint;
        public ServiceMetadata metadata;

        public ServiceRegistration() {
        }

        public ServiceRegistration(ServiceEndpoint endpoint, ServiceMetadata metadata) {
            this.endpoint = endpoint;
            this.metadata = metadata;
        }
    }
}
