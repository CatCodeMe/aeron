package io.aeron.rpc.annotation;

import io.aeron.rpc.RpcPattern;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an RPC endpoint.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcMethod {
    /**
     * Method name, if not specified will use method name
     */
    String name() default "";
    
    /**
     * Communication pattern
     */
    RpcPattern pattern() default RpcPattern.ONE_TO_ONE;
    
    /**
     * Method timeout in milliseconds, overrides service timeout
     */
    long timeout() default -1;
    
    /**
     * Whether the method is async
     */
    boolean async() default false;
    
    /**
     * Whether to enable retry
     */
    boolean retry() default false;
    
    /**
     * Max retry attempts
     */
    int maxRetries() default 3;
    
    /**
     * Retry backoff in milliseconds
     */
    long retryBackoff() default 1000;
    
    /**
     * Whether to enable circuit breaker
     */
    boolean circuitBreaker() default false;
    
    /**
     * Circuit breaker failure threshold
     */
    int failureThreshold() default 5;
    
    /**
     * Circuit breaker reset timeout in milliseconds
     */
    long resetTimeout() default 60000;
}
