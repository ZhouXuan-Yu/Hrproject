package com.hr.interview.repository;

import com.hr.interview.entity.InterviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface InterviewRecordRepository extends JpaRepository<InterviewRecord, Long>, JpaSpecificationExecutor<InterviewRecord> {

    Optional<InterviewRecord> findFirstByBookIdAndIsDeleted(Long bookId, Integer isDeleted);

    List<InterviewRecord> findByBookIdAndIsDeleted(Long bookId, Integer isDeleted);
}
