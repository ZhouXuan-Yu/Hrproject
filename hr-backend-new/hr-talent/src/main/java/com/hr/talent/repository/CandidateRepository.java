package com.hr.talent.repository;

import com.hr.talent.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {

    Optional<Candidate> findFirstByCandidateNameAndIsDeleted(String candidateName, Integer isDeleted);

    List<Candidate> findByIsDeleted(Integer isDeleted);
}
