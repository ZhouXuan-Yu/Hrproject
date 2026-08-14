package com.hr.config.repository;

import com.hr.config.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKeyAndIsDeleted(String configKey, Integer isDeleted);
}
