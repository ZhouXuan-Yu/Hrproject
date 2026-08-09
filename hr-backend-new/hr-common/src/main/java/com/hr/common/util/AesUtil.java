package com.hr.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * AES-256-GCM 加密工具，兼容 Flask crypto_utils.py。
 * 返回格式: hex(nonce + ciphertext)
 *
 * 解密时依次尝试两种密钥派生（历史兼容）：
 * 1. 截断/填充到 32 字节（本项目 Java 侧默认派生）
 * 2. sha256(secret)（Flask crypto_utils 派生）
 */
public final class AesUtil {

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_LENGTH = 12;

    private AesUtil() {
    }

    public static String encrypt(String plaintext, String secretKey) throws Exception {
        byte[] keyBytes = deriveKey(secretKey);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        byte[] nonce = new byte[NONCE_LENGTH];
        new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
        return HexFormat.of().formatHex(combined);
    }

    public static String decrypt(String hexValue, String secretKey) throws Exception {
        byte[] combined = HexFormat.of().parseHex(hexValue);
        // 1) 截断 32 字节派生（Java 侧）
        try {
            return decryptWithKey(combined, deriveKey(secretKey));
        } catch (Exception ignored) {
            // 2) sha256 派生（Flask 侧兼容）
        }
        return decryptWithKey(combined, deriveKeySha256(secretKey));
    }

    private static String decryptWithKey(byte[] combined, byte[] key) throws Exception {
        byte[] nonce = new byte[NONCE_LENGTH];
        System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] decrypted = cipher.doFinal(combined, NONCE_LENGTH, combined.length - NONCE_LENGTH);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static byte[] deriveKey(String secretKey) {
        byte[] src = secretKey.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        System.arraycopy(src, 0, key, 0, Math.min(src.length, 32));
        return key;
    }

    private static byte[] deriveKeySha256(String secretKey) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
