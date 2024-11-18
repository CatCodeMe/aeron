package io.aeron.rpc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an RPC service.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService {
    /**
     * Service name, if not specified will use class name
     */
    String name() default "";
    
    /**
     * Service version
     */
    String version() default "1.0.0";
    
    /**
     * Service group for load balancing
     */
    String group() default "default";
    
    /**
     * Service weight for load balancing
     */
    int weight() default 100;
    
    /**
     * Service timeout in milliseconds
     */
    long timeout() default 5000;
    
    /**
     * Whether to enable monitoring
     */
    boolean monitored() default true;
}
