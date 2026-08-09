package com.hr.auth.security;

import com.hr.common.util.Sha256Util;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * werkzeug 兼容密码校验器。
 * <p>
 * 支持格式：
 * <ul>
 *   <li>werkzeug scrypt: {@code scrypt:n:r:p$base64(salt)$base64(hash)}（默认 dklen=64）</li>
 *   <li>legacy SHA-256: 64 位 hex（sha256(password + salt)）</li>
 *   <li>bcrypt: {@code $2a$/$2b$/$2y$}</li>
 * </ul>
 * 当前数据库用户密码为 werkzeug scrypt（Flask generate_password_hash 默认算法）。
 */
@Component
public class WerkzeugPasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final String legacySalt;

    public WerkzeugPasswordEncoder(@Value("${password.salt:default-salt-change-me}") String legacySalt) {
        this.legacySalt = legacySalt;
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        if (storedHash.startsWith("scrypt:")) {
            return verifyScrypt(rawPassword, storedHash);
        }
        if (storedHash.startsWith("$2")) {
            return bcrypt.matches(rawPassword, storedHash);
        }
        if (isHex64(storedHash)) {
            // legacy SHA-256: sha256(password + salt)
            String candidate = Sha256Util.sha256Hex(rawPassword + legacySalt);
            return candidate.equalsIgnoreCase(storedHash);
        }
        return false;
    }

    private boolean verifyScrypt(String rawPassword, String storedHash) {
        try {
            // werkzeug 格式: scrypt:N:R:P$<16位明文salt>$<hex(hash)>
            // dklen 固定 64（hashlib.scrypt 默认）
            String body = storedHash.substring("scrypt:".length());
            String[] parts = body.split("\\$");
            if (parts.length != 3) {
                return false;
            }
            String[] params = parts[0].split(":");
            if (params.length != 3 && params.length != 4) {
                return false;
            }
            int n = Integer.parseInt(params[0]);
            int r = Integer.parseInt(params[1]);
            int p = Integer.parseInt(params[2]);

            byte[] salt = parts[1].getBytes(StandardCharsets.UTF_8);
            byte[] expected = hexDecode(parts[2]);

            byte[] computed = SCrypt.generate(
                    rawPassword.getBytes(StandardCharsets.UTF_8), salt, n, r, p, 64);
            return constantTimeEquals(computed, expected);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] hexDecode(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    private boolean isHex64(String s) {
        return s.length() == 64 && s.matches("[0-9a-fA-F]+");
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
