package org.book.commerce.bookcommerce.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AESUtil {
    private byte[] key;
    private SecretKeySpec secretKeySpec;

    static String IV = "";

    public AESUtil(@Value("${spring.aes.secret}") String rawKey){
        key = rawKey.getBytes(StandardCharsets.UTF_8);
        IV = rawKey.substring(0,16);
        secretKeySpec = new SecretKeySpec(key,"AES");
    }

    public String encrypt(String str) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(IV.getBytes()));
            return encodeBase64(cipher.doFinal(str.getBytes(StandardCharsets.UTF_8)));
        }catch (Exception e){
            throw new RuntimeException(); // 예외 처리 나중에
        }
    }
    public String decrypt(String str) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(IV.getBytes("UTF-8")));

            return new String(cipher.doFinal(decodeBase64(str)), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(); // 예외처리 나중에
        }
    }

    private String encodeBase64(byte[] source) {
        return Base64.getEncoder().encodeToString(source);
    }

    private byte[] decodeBase64(String encodedString) {
        return Base64.getDecoder().decode(encodedString);
    }
}
