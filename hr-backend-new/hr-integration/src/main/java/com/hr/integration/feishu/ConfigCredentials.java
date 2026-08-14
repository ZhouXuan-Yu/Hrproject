package com.hr.integration.feishu;

import com.hr.common.util.AesUtil;
import com.hr.config.entity.ApiKeyConfig;
import com.hr.config.repository.ApiKeyConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 从数据库配置表（t_hr_api_key）读取外部服务凭证并解密。
 * 供飞书/腾讯会议等集成客户端使用；环境变量优先于库表。
 */
@Slf4j
@Component
public class ConfigCredentials {

    private final ApiKeyConfigRepository apiKeyRepository;

    @Value("${crypto.secret-key:${SECRET_KEY:default-salt-change-me}}")
    private String cryptoSecretKey;

    public ConfigCredentials(ApiKeyConfigRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /** 返回解密后的密钥值；未配置或解密失败返回 null。 */
    public String get(String keyName) {
        ApiKeyConfig row = apiKeyRepository.findByKeyNameAndIsDeleted(keyName, 0).orElse(null);
        if (row == null || row.getValueEncrypted() == null || row.getValueEncrypted().isEmpty()) {
            return null;
        }
        // 解密密钥优先级（对齐 Flask：SECRET_KEY env var 解密 t_hr_api_key 加密值）
        // 1) Spring 属性 crypto.secret-key（来自 env var 或 application.yml）
        // 2) 系统环境变量
        // 3) 硬编码兜底
        String[] secrets = {
                cryptoSecretKey,                              // Spring 属性解析
                System.getenv("SECRET_KEY"),
                System.getenv("PASSWORD_SALT"),
                "dev-secret-key-a7f3b9c2e1d4-change-in-production", // .env 开发默认值
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
