package com.hr.talent.repository;

import com.hr.talent.entity.ResumeMatch;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface ResumeMatchRepository extends JpaRepository<ResumeMatch, Long>, JpaSpecificationExecutor<ResumeMatch> {

    List<ResumeMatch> findByResumeIdInAndDemandIdAndIsDeleted(
            Collection<Long> resumeIds, Long demandId, Integer isDeleted, Sort sort);

    List<ResumeMatch> findByResumeIdInAndIsDeleted(Collection<Long> resumeIds, Integer isDeleted);
}
