package io.aeron.rpc.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT-based authentication provider implementation.
 */
public class JwtAuthenticationProvider implements AuthenticationProvider {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationProvider.class);
    private final SecretKey secretKey;
    private final Map<String, AuthToken> tokenStore = new ConcurrentHashMap<>();
    private final long tokenValidityMinutes;

    public JwtAuthenticationProvider(String secret, long tokenValidityMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenValidityMinutes = tokenValidityMinutes;
    }

    @Override
    public CompletableFuture<AuthToken> authenticate(Map<String, String> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate credentials here
                String username = credentials.get("username");
                if (username == null) {
                    throw new SecurityException("Username is required");
                }

                Instant expiration = Instant.now().plus(tokenValidityMinutes, ChronoUnit.MINUTES);
                String jwtToken = Jwts.builder()
                        .setSubject(username)
                        .setIssuedAt(new Date())
                        .setExpiration(Date.from(expiration))
                        .signWith(secretKey)
                        .compact();

                AuthToken token = AuthToken.builder()
                        .withTokenValue(jwtToken)
                        .withExpiration(expiration)
                        .withClaim("username", username)
                        .build();

                tokenStore.put(jwtToken, token);
                return token;
            } catch (Exception e) {
                logger.error("Authentication failed", e);
                throw new SecurityException("Authentication failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> validateToken(AuthToken token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (token.isExpired()) {
                    return false;
                }

                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(secretKey)
                        .build()
                        .parseClaimsJws(token.getTokenValue())
                        .getBody();

                return !claims.getExpiration().before(new Date());
            } catch (Exception e) {
                logger.error("Token validation failed", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> revokeToken(AuthToken token) {
        return CompletableFuture.runAsync(() -> {
            tokenStore.remove(token.getTokenValue());
        });
    }

    @Override
    public CompletableFuture<AuthToken> refreshToken(AuthToken token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(secretKey)
                        .build()
                        .parseClaimsJws(token.getTokenValue())
                        .getBody();

                Instant newExpiration = Instant.now().plus(tokenValidityMinutes, ChronoUnit.MINUTES);
                String newJwtToken = Jwts.builder()
                        .setSubject(claims.getSubject())
                        .setIssuedAt(new Date())
                        .setExpiration(Date.from(newExpiration))
                        .signWith(secretKey)
                        .compact();

                AuthToken newToken = AuthToken.builder()
                        .withTokenValue(newJwtToken)
                        .withExpiration(newExpiration)
                        .withClaim("username", claims.getSubject())
                        .build();

                tokenStore.put(newJwtToken, newToken);
                tokenStore.remove(token.getTokenValue());

                return newToken;
            } catch (Exception e) {
                logger.error("Token refresh failed", e);
                throw new SecurityException("Token refresh failed", e);
            }
        });
    }
}
