package bot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-256-GCM for x-ui panel passwords stored in the servers table.
 * Same format as the site (site.service.ServerPasswordCrypto): "enc:v1:" + base64(nonce | ct).
 * Key from SERVER_CRYPTO_KEY env (base64, 32 bytes). If unset, the bot runs in legacy
 * passthrough mode: values without the prefix are returned as-is and writes stay plaintext.
 */
public final class Crypto {

    public static final String PREFIX = "enc:v1:";

    private static final Logger log = LoggerFactory.getLogger(Crypto.class);
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static SecretKeySpec key;

    static {
        String env = System.getenv("SERVER_CRYPTO_KEY");
        if (env != null && !env.isBlank()) {
            try {
                byte[] raw = Base64.getDecoder().decode(env.trim());
                if (raw.length == 32) {
                    key = new SecretKeySpec(raw, "AES");
                } else {
                    log.warn("SERVER_CRYPTO_KEY decodes to {} bytes, expected 32 — running without decryption", raw.length);
                }
            } catch (IllegalArgumentException e) {
                log.warn("SERVER_CRYPTO_KEY is not valid base64 — running without decryption");
            }
        } else {
            log.warn("SERVER_CRYPTO_KEY is not set — server passwords will be used as stored (legacy mode)");
        }
    }

    private Crypto() {}

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** Encrypts unless the value is already encrypted or no key is configured. */
    public static String encryptIfPossible(String plaintext) {
        if (plaintext == null || isEncrypted(plaintext) || key == null) {
            return plaintext;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            java.security.SecureRandom.getInstanceStrong().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            log.error("Failed to encrypt server password", e);
            return plaintext;
        }
    }

    /** Decrypts an encrypted value; legacy plaintext and missing key pass through. */
    public static String decryptIfPossible(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        if (key == null) {
            log.error("Server password is encrypted but SERVER_CRYPTO_KEY is not set — cannot login to x-ui panel");
            return stored;
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(in, 0, nonce, 0, nonce.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] pt = cipher.doFinal(in, nonce.length, in.length - nonce.length);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt server password", e);
            return stored;
        }
    }
}
