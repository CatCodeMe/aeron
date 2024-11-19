package io.aeron.rpc.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AES-GCM encryption provider implementation.
 * Provides authenticated encryption with associated data (AEAD).
 */
public class AesGcmEncryptionProvider implements EncryptionProvider {
    private static final Logger logger = LoggerFactory.getLogger(AesGcmEncryptionProvider.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private final int keySize;
    private final AtomicReference<SecretKey> currentKey;
    private final SecureRandom secureRandom;

    public AesGcmEncryptionProvider(int keySize) {
        if (keySize != 128 && keySize != 256) {
            throw new IllegalArgumentException("Key size must be either 128 or 256 bits");
        }
        this.keySize = keySize;
        this.secureRandom = new SecureRandom();
        this.currentKey = new AtomicReference<>(generateKey());
    }

    @Override
    public CompletableFuture<byte[]> encrypt(byte[] message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] iv = new byte[GCM_IV_LENGTH];
                secureRandom.nextBytes(iv);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.ENCRYPT_MODE, currentKey.get(), parameterSpec);

                byte[] ciphertext = cipher.doFinal(message);
                byte[] encrypted = new byte[iv.length + ciphertext.length];
                System.arraycopy(iv, 0, encrypted, 0, iv.length);
                System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);

                return encrypted;
            } catch (Exception e) {
                logger.error("Encryption failed", e);
                throw new SecurityException("Encryption failed", e);
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> decrypt(byte[] encryptedMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] iv = new byte[GCM_IV_LENGTH];
                System.arraycopy(encryptedMessage, 0, iv, 0, iv.length);

                byte[] ciphertext = new byte[encryptedMessage.length - GCM_IV_LENGTH];
                System.arraycopy(encryptedMessage, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

                Cipher cipher = Cipher.getInstance(ALGORITHM);
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, currentKey.get(), parameterSpec);

                return cipher.doFinal(ciphertext);
            } catch (Exception e) {
                logger.error("Decryption failed", e);
                throw new SecurityException("Decryption failed", e);
            }
        });
    }

    @Override
    public String getAlgorithm() {
        return ALGORITHM;
    }

    @Override
    public int getKeySize() {
        return keySize;
    }

    @Override
    public CompletableFuture<Void> rotateKeys() {
        return CompletableFuture.runAsync(() -> {
            try {
                SecretKey newKey = generateKey();
                currentKey.set(newKey);
                logger.info("Encryption key rotated successfully");
            } catch (Exception e) {
                logger.error("Key rotation failed", e);
                throw new SecurityException("Key rotation failed", e);
            }
        });
    }

    private SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(keySize, secureRandom);
            return keyGen.generateKey();
        } catch (Exception e) {
            logger.error("Key generation failed", e);
            throw new SecurityException("Key generation failed", e);
        }
    }
}
