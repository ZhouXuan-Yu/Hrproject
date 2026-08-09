package com.hr.auth.security;

import com.hr.auth.service.UserManagementService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 werkzeug 兼容密码校验（scrypt / bcrypt / legacy sha256）。
 */
class WerkzeugPasswordEncoderTest {

    private final WerkzeugPasswordEncoder encoder = new WerkzeugPasswordEncoder("test-salt-12345");

    @Test
    void scrypt_roundTrip() {
        String hash = UserManagementService.werkzeugScrypt("123456");
        assertTrue(hash.startsWith("scrypt:"));
        assertTrue(encoder.matches("123456", hash));
        assertFalse(encoder.matches("wrong-password", hash));
    }

    @Test
    void scrypt_wrongPassword_rejected() {
        String hash = UserManagementService.werkzeugScrypt("abc123");
        assertFalse(encoder.matches("abc124", hash));
    }

    @Test
    void bcrypt_supported() {
        // $2b$ 前缀（werkzeug bcrypt 输出）
        String bcryptHash = "$2b$12$k42ZFKIW9nErF0E2S1PO9uVZ2R9yD0Y0X9X8W3wC0c5kQ6G9vUj2e";
        // 用 BCryptPasswordEncoder 生成一个真实的可校验哈希
        String real = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .encode("123456");
        assertTrue(real.startsWith("$2"));
        assertTrue(encoder.matches("123456", real));
    }

    @Test
    void legacySha256_supported() {
        // sha256(password + salt)
        String legacy = com.hr.common.util.Sha256Util.sha256Hex("123456" + "test-salt-12345");
        assertTrue(encoder.matches("123456", legacy));
        assertFalse(encoder.matches("654321", legacy));
    }

    @Test
    void nullOrEmpty_returnsFalse() {
        assertFalse(encoder.matches(null, "scrypt:16384:16:1$abc$def"));
        assertFalse(encoder.matches("pwd", null));
        assertFalse(encoder.matches("pwd", ""));
        assertFalse(encoder.matches("pwd", "not-a-hash-format"));
    }
}
