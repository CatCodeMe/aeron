package io.aeron.rpc.security;

import io.aeron.rpc.RpcMessage;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Security provider for RPC communication.
 */
public class RpcSecurity {
    private final SecretKey jwtKey;
    private final KeyPair tlsKeyPair;
    private final long tokenValidityMs;

    public RpcSecurity(byte[] jwtSecret, KeyPair tlsKeyPair, long tokenValidityMs) {
        this.jwtKey = Keys.hmacShaKeyFor(jwtSecret);
        this.tlsKeyPair = tlsKeyPair;
        this.tokenValidityMs = tokenValidityMs;
    }

    /**
     * Generate a JWT token for authentication.
     */
    public String generateToken(String subject, String... roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + tokenValidityMs);

        return Jwts.builder()
            .setSubject(subject)
            .claim("roles", String.join(",", roles))
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(jwtKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Validate a JWT token.
     */
    public Claims validateToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(jwtKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    /**
     * Encrypt a message using TLS public key.
     */
    public byte[] encrypt(byte[] data) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, tlsKeyPair.getPublic());
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new SecurityException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt a message using TLS private key.
     */
    public byte[] decrypt(byte[] data) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, tlsKeyPair.getPrivate());
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new SecurityException("Failed to decrypt data", e);
        }
    }

    /**
     * Sign a message using TLS private key.
     */
    public byte[] sign(byte[] data) {
        try {
            java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initSign(tlsKeyPair.getPrivate());
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new SecurityException("Failed to sign data", e);
        }
    }

    /**
     * Verify a message signature using TLS public key.
     */
    public boolean verify(byte[] data, byte[] signature) {
        try {
            java.security.Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
            verifier.initVerify(tlsKeyPair.getPublic());
            verifier.update(data);
            return verifier.verify(signature);
        } catch (Exception e) {
            throw new SecurityException("Failed to verify signature", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private byte[] jwtSecret;
        private KeyPair tlsKeyPair;
        private long tokenValidityMs = TimeUnit.HOURS.toMillis(1);

        public Builder jwtSecret(byte[] secret) {
            this.jwtSecret = secret;
            return this;
        }

        public Builder tlsKeyPair(KeyPair keyPair) {
            this.tlsKeyPair = keyPair;
            return this;
        }

        public Builder tokenValidity(long duration, TimeUnit unit) {
            this.tokenValidityMs = unit.toMillis(duration);
            return this;
        }

        public RpcSecurity build() {
            if (jwtSecret == null) {
                jwtSecret = Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded();
            }
            if (tlsKeyPair == null) {
                tlsKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
            }
            return new RpcSecurity(jwtSecret, tlsKeyPair, tokenValidityMs);
        }
    }

    /**
     * Create a secure message wrapper.
     */
    public SecureMessage secure(RpcMessage message, String token) {
        byte[] data = message.toString().getBytes();
        byte[] signature = sign(data);
        byte[] encrypted = encrypt(data);

        return new SecureMessage(encrypted, signature, token);
    }

    /**
     * Verify and extract message from secure wrapper.
     */
    public RpcMessage verify(SecureMessage secureMessage) {
        // Validate token
        Claims claims = validateToken(secureMessage.token);
        
        // Decrypt data
        byte[] decrypted = decrypt(secureMessage.encrypted);
        
        // Verify signature
        if (!verify(decrypted, secureMessage.signature)) {
            throw new SecurityException("Invalid message signature");
        }

        // Parse message
        return RpcMessage.fromBytes(decrypted);
    }

    public static class SecureMessage {
        public final byte[] encrypted;
        public final byte[] signature;
        public final String token;

        public SecureMessage(byte[] encrypted, byte[] signature, String token) {
            this.encrypted = encrypted;
            this.signature = signature;
            this.token = token;
        }
    }
}
