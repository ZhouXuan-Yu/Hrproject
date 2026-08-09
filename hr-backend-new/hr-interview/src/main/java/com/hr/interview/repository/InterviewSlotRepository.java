package com.hr.interview.repository;

import com.hr.interview.entity.InterviewSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, Long>, JpaSpecificationExecutor<InterviewSlot> {
}
