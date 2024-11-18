package io.aeron.rpc.fallback;

import io.aeron.rpc.RpcPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Fallback handler for service degradation.
 */
public class FallbackHandler {
    private static final Logger logger = LoggerFactory.getLogger(FallbackHandler.class);
    
    private final Map<String, Function<Object[], Object>> fallbacks;
    private final Map<String, Boolean> degradedServices;

    public FallbackHandler() {
        this.fallbacks = new ConcurrentHashMap<>();
        this.degradedServices = new ConcurrentHashMap<>();
    }

    public void registerFallback(String serviceName, String methodName, Function<Object[], Object> fallback) {
        String key = buildKey(serviceName, methodName);
        fallbacks.put(key, fallback);
    }

    public void registerFallback(Class<?> serviceClass, Method method, Function<Object[], Object> fallback) {
        String key = buildKey(serviceClass.getSimpleName(), method.getName());
        fallbacks.put(key, fallback);
    }

    public void markDegraded(String serviceName, boolean degraded) {
        degradedServices.put(serviceName, degraded);
        logger.info("Service {} marked as {}", serviceName, degraded ? "degraded" : "normal");
    }

    public boolean isDegraded(String serviceName) {
        return degradedServices.getOrDefault(serviceName, false);
    }

    public Object executeFallback(String serviceName, String methodName, Object[] args) {
        String key = buildKey(serviceName, methodName);
        Function<Object[], Object> fallback = fallbacks.get(key);
        
        if (fallback == null) {
            throw new IllegalStateException("No fallback registered for " + key);
        }
        
        try {
            logger.debug("Executing fallback for {}", key);
            return fallback.apply(args);
        } catch (Exception e) {
            logger.error("Error executing fallback for " + key, e);
            throw e;
        }
    }

    private String buildKey(String serviceName, String methodName) {
        return serviceName + "." + methodName;
    }

    public void clear() {
        fallbacks.clear();
        degradedServices.clear();
    }

    // Common fallbacks for different patterns
    public static class CommonFallbacks {
        public static <T> Function<Object[], T> returnNull() {
            return args -> null;
        }

        public static <T> Function<Object[], T> returnDefault(T defaultValue) {
            return args -> defaultValue;
        }

        public static Function<Object[], Boolean> returnFalse() {
            return args -> false;
        }

        public static Function<Object[], Void> noOp() {
            return args -> null;
        }

        public static <T> Function<Object[], T> cacheValue(T cachedValue) {
            return args -> cachedValue;
        }
    }
}
