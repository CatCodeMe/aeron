package io.aeron.rpc.security;

import java.util.concurrent.CompletableFuture;

/**
 * Encryption provider interface for RPC security.
 * Handles message encryption and decryption.
 */
public interface EncryptionProvider {
    /**
     * Encrypt a message.
     *
     * @param message Message to encrypt
     * @return Future containing encrypted message
     */
    CompletableFuture<byte[]> encrypt(byte[] message);

    /**
     * Decrypt a message.
     *
     * @param encryptedMessage Encrypted message to decrypt
     * @return Future containing decrypted message
     */
    CompletableFuture<byte[]> decrypt(byte[] encryptedMessage);

    /**
     * Get the encryption algorithm name.
     *
     * @return Name of the encryption algorithm
     */
    String getAlgorithm();

    /**
     * Get the key size in bits.
     *
     * @return Key size in bits
     */
    int getKeySize();

    /**
     * Rotate encryption keys.
     *
     * @return Future completing when key rotation is done
     */
    CompletableFuture<Void> rotateKeys();
}
