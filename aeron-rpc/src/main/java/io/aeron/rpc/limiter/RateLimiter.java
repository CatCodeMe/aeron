package io.aeron.rpc.limiter;

import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter for RPC calls.
 */
public class RpcRateLimiter {
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    
    public boolean tryAcquire(String key, double permitsPerSecond, long timeout, TimeUnit unit) {
        RateLimiter limiter = limiters.computeIfAbsent(key, 
            k -> RateLimiter.create(permitsPerSecond));
        return limiter.tryAcquire(1, timeout, unit);
    }
    
    public void setRate(String key, double permitsPerSecond) {
        RateLimiter limiter = limiters.computeIfAbsent(key,
            k -> RateLimiter.create(permitsPerSecond));
        limiter.setRate(permitsPerSecond);
    }
    
    public void remove(String key) {
        limiters.remove(key);
    }
    
    public void clear() {
        limiters.clear();
    }
}
