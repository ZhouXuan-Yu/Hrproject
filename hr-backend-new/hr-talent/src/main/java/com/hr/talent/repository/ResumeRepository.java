package com.hr.talent.repository;

import com.hr.talent.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume, Long>, JpaSpecificationExecutor<Resume> {

    List<Resume> findByCandidateIdAndIsDeletedOrderByStorageTimeDesc(Long candidateId, Integer isDeleted);

    List<Resume> findByCandidateIdInAndIsDeletedOrderByStorageTimeDesc(
            java.util.Collection<Long> candidateIds, Integer isDeleted);
}
