package com.hr.config.repository;

import com.hr.config.entity.AiKnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiKnowledgeBaseRepository extends JpaRepository<AiKnowledgeBase, Long> {

    Optional<AiKnowledgeBase> findFirstByStatusAndIsDeletedOrderByIdDesc(Integer status, Integer isDeleted);
}
