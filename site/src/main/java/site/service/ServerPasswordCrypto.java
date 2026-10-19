package site.service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for x-ui panel passwords stored in the servers table.
 * Ciphertext format: "enc:v1:" + base64(nonce | ciphertext+tag).
 * Values without the prefix are treated as legacy plaintext and returned as-is.
 * Key comes from the SERVER_CRYPTO_KEY env variable (base64, 32 bytes).
 */
public final class ServerPasswordCrypto {

    public static final String PREFIX = "enc:v1:";

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile SecretKeySpec key;

    private ServerPasswordCrypto() {}

    private static SecretKeySpec key() {
        SecretKeySpec k = key;
        if (k == null) {
            synchronized (ServerPasswordCrypto.class) {
                k = key;
                if (k == null) {
                    String env = System.getenv("SERVER_CRYPTO_KEY");
                    if (env == null || env.isBlank()) {
                        throw new IllegalStateException(
                            "SERVER_CRYPTO_KEY is not set: cannot encrypt/decrypt server passwords");
                    }
                    byte[] raw = Base64.getDecoder().decode(env.trim());
                    if (raw.length != KEY_BYTES) {
                        throw new IllegalStateException(
                            "SERVER_CRYPTO_KEY must decode to 32 bytes, got " + raw.length);
                    }
                    k = key = new SecretKeySpec(raw, "AES");
                }
            }
        }
        return k;
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt server password", e);
        }
    }

    /** Decrypts a stored value; legacy plaintext (no prefix) is returned unchanged. */
    public static String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) return stored;
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(in, 0, nonce, 0, nonce.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] pt = cipher.doFinal(in, nonce.length, in.length - nonce.length);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt server password", e);
        }
    }
}
