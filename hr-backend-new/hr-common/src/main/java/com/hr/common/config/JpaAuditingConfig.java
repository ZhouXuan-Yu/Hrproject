package com.hr.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * JPA 审计配置：自动填充 createdBy/updatedBy。
 * 从 SecurityContext 读取当前用户 ID。
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> {
            try {
                Long userId = com.hr.common.util.SecurityUtils.getUserId();
                return Optional.ofNullable(userId);
            } catch (Exception e) {
                return Optional.empty();
            }
        };
    }
}
