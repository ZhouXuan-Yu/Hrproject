package com.hr.talent.repository;

import com.hr.talent.entity.CandidateTagRel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateTagRelRepository extends JpaRepository<CandidateTagRel, Long> {

    List<CandidateTagRel> findByCandidateId(Long candidateId);

    List<CandidateTagRel> findByCandidateIdAndTagId(Long candidateId, Long tagId);
}
