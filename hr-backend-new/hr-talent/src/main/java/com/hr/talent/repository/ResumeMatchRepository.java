package com.hr.talent.repository;

import com.hr.talent.entity.ResumeMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ResumeMatchRepository extends JpaRepository<ResumeMatch, Long>, JpaSpecificationExecutor<ResumeMatch> {
}
