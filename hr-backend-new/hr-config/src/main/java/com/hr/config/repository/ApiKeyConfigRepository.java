package com.hr.config.repository;

import com.hr.config.entity.ApiKeyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyConfigRepository extends JpaRepository<ApiKeyConfig, Long> {

    Optional<ApiKeyConfig> findByKeyNameAndIsDeleted(String keyName, Integer isDeleted);
}
