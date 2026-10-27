package com.iitrpr.calendar.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Encrypts Google refresh tokens with AES-256-GCM (random IV per value, tamper-proof). */
@Component
public class TokenCrypto {

    private final AppSecrets secrets;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec key;

    public TokenCrypto(AppSecrets secrets) {
        this.secrets = secrets;
    }

    private synchronized SecretKeySpec key() throws Exception {
        if (key == null) {
            String material = secrets.tokenKeyMaterial();
            if (material == null || material.isEmpty()) {
                throw new IllegalStateException("Encryption key is not configured.");
            }
            byte[] k = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(k, "AES");
        }
        return key;
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt token", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[12];
            System.arraycopy(in, 0, iv, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt token", e);
        }
    }
}
