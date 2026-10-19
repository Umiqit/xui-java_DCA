package site.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.dto.TelegramAuthData;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

@Service
public class TelegramAuthService {

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthService.class);

    private static final long FUTURE_TOLERANCE_SECONDS = 60;

    @Value("${telegram.bot-token}")
    private String botToken;

    @Value("${telegram.auth-max-age-seconds:600}")
    private long authMaxAgeSeconds;

    @PostConstruct
    public void init() {
        if (botToken == null || botToken.isBlank()) {
            log.error("BOT_TOKEN is not set! Telegram auth will fail.");
        } else {
            log.info("TelegramAuthService initialized. Token length: {}", botToken.length());
        }
    }

    public boolean verify(TelegramAuthData data) {
        if (botToken == null || botToken.isBlank()) {
            log.error("Cannot verify Telegram auth: BOT_TOKEN is empty");
            return false;
        }

        if (!isAuthDateFresh(data.getAuthDate())) {
            log.info("Telegram auth failed for tgId={}: stale auth_date", data.getId());
            return false;
        }

        Map<String, String> fields = new TreeMap<>();
        fields.put("auth_date", String.valueOf(data.getAuthDate()));
        if (data.getFirstName() != null && !data.getFirstName().isBlank()) {
            fields.put("first_name", data.getFirstName());
        }
        fields.put("id", String.valueOf(data.getId()));
        if (data.getLastName() != null && !data.getLastName().isBlank()) {
            fields.put("last_name", data.getLastName());
        }
        if (data.getPhotoUrl() != null && !data.getPhotoUrl().isBlank()) {
            fields.put("photo_url", data.getPhotoUrl());
        }
        if (data.getUsername() != null && !data.getUsername().isBlank()) {
            fields.put("username", data.getUsername());
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        String dataCheckString = sb.toString();
        log.debug("dataCheckString: {}", dataCheckString);

        String receivedHash = data.getHash();
        if (receivedHash == null || receivedHash.isBlank()) {
            log.info("Telegram auth failed for tgId={}: hash is empty", data.getId());
            return false;
        }
        receivedHash = receivedHash.trim().toLowerCase();

        try {
            // Login Widget uses SHA256(botToken) as secret key
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] secretKeyBytes = digest.digest(botToken.getBytes(StandardCharsets.UTF_8));

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secretKeyBytes, "HmacSHA256");
            mac.init(secretKey);
            byte[] hashBytes = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

            String computedHash = bytesToHex(hashBytes);
            log.debug("Computed hash: {}, received hash: {}", computedHash, receivedHash);

            boolean ok = MessageDigest.isEqual(
                    computedHash.getBytes(StandardCharsets.UTF_8),
                    receivedHash.getBytes(StandardCharsets.UTF_8));
            if (ok) {
                log.info("Telegram auth ok for tgId={}", data.getId());
            } else {
                log.info("Telegram auth failed for tgId={}: hash mismatch", data.getId());
            }
            return ok;
        } catch (Exception e) {
            log.error("Error verifying Telegram auth for tgId=" + data.getId(), e);
            return false;
        }
    }

    private boolean isAuthDateFresh(long authDate) {
        long now = System.currentTimeMillis() / 1000L;
        long age = now - authDate;
        if (age > authMaxAgeSeconds) {
            log.debug("auth_date is stale: age={}s (max={}s)", age, authMaxAgeSeconds);
            return false;
        }
        if (age < -FUTURE_TOLERANCE_SECONDS) {
            log.debug("auth_date is in the future: age={}s (tolerance={}s)", age, FUTURE_TOLERANCE_SECONDS);
            return false;
        }
        return true;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
