package com.hr.config.repository;

import com.hr.config.entity.NotifyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotifyTemplateRepository extends JpaRepository<NotifyTemplate, Long> {

    List<NotifyTemplate> findByStatusAndIsDeletedOrderByUpdatedAtDesc(Integer status, Integer isDeleted);
}
