package com.hr.talent.repository;

import com.hr.talent.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FileEntityRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {
}
