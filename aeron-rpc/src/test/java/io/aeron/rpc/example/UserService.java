package io.aeron.rpc.example;

import io.aeron.rpc.annotation.RpcService;
import io.aeron.rpc.annotation.RpcMethod;
import io.aeron.rpc.RpcPattern;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@RpcService(
    name = "UserService",
    version = "1.0.0",
    group = "users",
    monitored = true
)
public class UserService {
    private final Map<String, User> users = new ConcurrentHashMap<>();

    @RpcMethod(pattern = RpcPattern.ONE_TO_ONE)
    public User getUser(String userId) {
        return users.get(userId);
    }

    @RpcMethod(
        pattern = RpcPattern.ONE_TO_ONE,
        async = true,
        retry = true,
        circuitBreaker = true
    )
    public CompletableFuture<User> createUser(User user) {
        return CompletableFuture.supplyAsync(() -> {
            users.put(user.getId(), user);
            return user;
        });
    }

    @RpcMethod(pattern = RpcPattern.ONE_TO_MANY)
    public void broadcastUserUpdate(User user) {
        users.put(user.getId(), user);
    }

    public static class User {
        private String id;
        private String name;
        private String email;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}
