package site.service;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class ServerPasswordConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(ServerPasswordConverter.class);

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || ServerPasswordCrypto.isEncrypted(attribute)) {
            return attribute;
        }
        String encrypted = ServerPasswordCrypto.encrypt(attribute);
        log.info("Server password encrypted (legacy plaintext migrated to ciphertext)");
        return encrypted;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        // Keep the ciphertext in the entity; decryption happens only where the
        // plaintext is actually needed, so it never leaks into templates/logs.
        return dbData;
    }
}
