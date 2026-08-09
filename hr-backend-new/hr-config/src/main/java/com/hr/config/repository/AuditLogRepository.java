package com.hr.config.repository;

import com.hr.config.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByIsDeletedOrderByOperateTimeDesc(Integer isDeleted);
}
