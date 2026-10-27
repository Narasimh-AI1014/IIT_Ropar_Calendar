package com.iitrpr.calendar.security;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncryptionUtil {
    
    // A stable, 256-bit development key anchor (Must be kept private in production environment)
    private static final String SECRET_KEY_SEED = "IITRPR_Acad_Calendar_SecretKey32B"; 
    private static final String ALGORITHM = "AES";

    public static String encrypt(String plainText) throws Exception {
        if (plainText == null) return null;
        SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY_SEED.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}
