package com.hr.integration.feishu;

import com.hr.common.util.AesUtil;
import com.hr.config.entity.ApiKeyConfig;
import com.hr.config.repository.ApiKeyConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 从数据库配置表（t_hr_api_key）读取外部服务凭证并解密。
 * 供飞书/腾讯会议等集成客户端使用；环境变量优先于库表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigCredentials {

    private final ApiKeyConfigRepository apiKeyRepository;

    /** 返回解密后的密钥值；未配置或解密失败返回 null。 */
    public String get(String keyName) {
        ApiKeyConfig row = apiKeyRepository.findByKeyNameAndIsDeleted(keyName, 0).orElse(null);
        if (row == null || row.getValueEncrypted() == null || row.getValueEncrypted().isEmpty()) {
            return null;
        }
        // 历史兼容：secret 是 Flask 时代用 SECRET_KEY 加密，app_id 是 Java 用 PASSWORD_SALT 加密。
        // 依次尝试两个密钥 + 两种派生方式。
        String[] secrets = {
                System.getenv("PASSWORD_SALT"),
                System.getenv("SECRET_KEY"),
                "default-salt-change-me",
                "change-me-in-production",
        };
        for (String secret : secrets) {
            if (secret == null || secret.isBlank()) {
                continue;
            }
            try {
                return AesUtil.decrypt(row.getValueEncrypted(), secret);
            } catch (Exception ignored) {
                // try next secret
            }
        }
        log.debug("解密配置密钥 {} 失败（已尝试全部密钥）", keyName);
        return null;
    }
}
