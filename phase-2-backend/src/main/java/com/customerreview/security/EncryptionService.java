package com.customerreview.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class EncryptionService {

    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] secretKey;
    private final byte[] hmacKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(
            @Value("${app.security.encryption-key:0123456789abcdef0123456789abcdef}") String encKeyHex,
            @Value("${app.security.hmac-key:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}") String hmacKeyHex) {
        
        byte[] parsedEnc;
        byte[] parsedHmac;
        try {
            parsedEnc = HexFormat.of().parseHex(encKeyHex.trim());
        } catch (Exception e) {
            parsedEnc = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        }
        try {
            parsedHmac = HexFormat.of().parseHex(hmacKeyHex.trim());
        } catch (Exception e) {
            parsedHmac = "1234567890123456789012345678901234567890123456789012345678901234".getBytes(StandardCharsets.UTF_8);
        }
        this.secretKey = parsedEnc;
        this.hmacKey = parsedHmac;
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            return byteBuffer.array();
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting field", e);
        }
    }

    public String decrypt(byte[] encrypted) {
        if (encrypted == null || encrypted.length <= GCM_IV_LENGTH) {
            return null;
        }
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting field", e);
        }
    }

    public String hmacSha256(String input) {
        if (input == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(hmacKey, HMAC_ALGO);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(input.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error computing HMAC", e);
        }
    }
}
