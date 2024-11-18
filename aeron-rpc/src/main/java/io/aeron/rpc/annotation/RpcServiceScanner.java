package io.aeron.rpc.annotation;

import io.aeron.rpc.RpcServer;
import io.aeron.rpc.ServiceMetadata;
import io.aeron.rpc.versioning.ServiceVersion;
import org.reflections.Reflections;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scanner for RPC service annotations.
 */
public class RpcServiceScanner {
    private static final Logger logger = LoggerFactory.getLogger(RpcServiceScanner.class);
    private final RpcServer rpcServer;
    private final Map<String, Object> serviceInstances;
    private final String basePackage;

    public RpcServiceScanner(RpcServer rpcServer, String basePackage) {
        this.rpcServer = rpcServer;
        this.basePackage = basePackage;
        this.serviceInstances = new ConcurrentHashMap<>();
    }

    public void scan() {
        logger.info("Scanning for RPC services in package: {}", basePackage);
        
        Reflections reflections = new Reflections(new ConfigurationBuilder()
            .setUrls(ClasspathHelper.forPackage(basePackage))
            .setScanners(new TypeAnnotationsScanner()));

        Set<Class<?>> serviceClasses = reflections.getTypesAnnotatedWith(RpcService.class);
        
        for (Class<?> serviceClass : serviceClasses) {
            try {
                registerService(serviceClass);
            } catch (Exception e) {
                logger.error("Failed to register service: " + serviceClass.getName(), e);
            }
        }
    }

    private void registerService(Class<?> serviceClass) throws Exception {
        RpcService serviceAnnotation = serviceClass.getAnnotation(RpcService.class);
        String serviceName = getServiceName(serviceClass, serviceAnnotation);
        
        // Create service instance
        Object serviceInstance = serviceClass.getDeclaredConstructor().newInstance();
        serviceInstances.put(serviceName, serviceInstance);
        
        // Build service metadata
        ServiceMetadata.Builder metadataBuilder = ServiceMetadata.builder()
            .name(serviceName)
            .version(ServiceVersion.parse(serviceAnnotation.version()))
            .group(serviceAnnotation.group())
            .weight(serviceAnnotation.weight());
            
        // Process methods
        for (Method method : serviceClass.getDeclaredMethods()) {
            RpcMethod methodAnnotation = method.getAnnotation(RpcMethod.class);
            if (methodAnnotation != null) {
                registerMethod(serviceInstance, method, methodAnnotation, metadataBuilder);
            }
        }
        
        // Register with RPC server
        rpcServer.registerService(metadataBuilder.build(), serviceInstance);
        logger.info("Registered RPC service: {}", serviceName);
    }

    private void registerMethod(Object serviceInstance, Method method, RpcMethod annotation, 
                              ServiceMetadata.Builder metadataBuilder) {
        String methodName = annotation.name().isEmpty() ? method.getName() : annotation.name();
        
        metadataBuilder.addMethod(methodName, builder -> builder
            .pattern(annotation.pattern())
            .timeout(annotation.timeout())
            .async(annotation.async())
            .retry(annotation.retry())
            .maxRetries(annotation.maxRetries())
            .retryBackoff(annotation.retryBackoff())
            .circuitBreaker(annotation.circuitBreaker())
            .failureThreshold(annotation.failureThreshold())
            .resetTimeout(annotation.resetTimeout())
            .build());
            
        logger.debug("Registered RPC method: {}.{}", method.getDeclaringClass().getSimpleName(), methodName);
    }

    private String getServiceName(Class<?> serviceClass, RpcService annotation) {
        return annotation.name().isEmpty() ? serviceClass.getSimpleName() : annotation.name();
    }

    public Object getServiceInstance(String serviceName) {
        return serviceInstances.get(serviceName);
    }

    public Collection<Object> getServiceInstances() {
        return serviceInstances.values();
    }
}
