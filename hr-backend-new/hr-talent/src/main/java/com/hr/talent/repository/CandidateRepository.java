package com.hr.talent.repository;

import com.hr.talent.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {

    Optional<Candidate> findFirstByCandidateNameAndIsDeleted(String candidateName, Integer isDeleted);

    List<Candidate> findByIsDeleted(Integer isDeleted);

    List<Candidate> findByMobileHashAndIsDeleted(String mobileHash, Integer isDeleted);

    List<Candidate> findByEmailIgnoreCaseAndIsDeleted(String email, Integer isDeleted);

    List<Candidate> findByCandidateNameIgnoreCaseAndIsDeleted(String candidateName, Integer isDeleted);

    /** 按 mobile_hash 分组，返回 count > 1 的组 */
    @Query(value = "SELECT c.mobile_hash, COUNT(c.id) AS cnt FROM t_hr_candidate c " +
            "WHERE c.mobile_hash IS NOT NULL AND c.mobile_hash != '' AND c.is_deleted = 0 " +
            "GROUP BY c.mobile_hash HAVING COUNT(c.id) > 1", nativeQuery = true)
    List<Object[]> findDuplicateMobileHashGroups();

    /** 按 LOWER(email) 分组，返回 count > 1 的组 */
    @Query(value = "SELECT LOWER(c.email) AS email_lower, COUNT(c.id) AS cnt FROM t_hr_candidate c " +
            "WHERE c.email IS NOT NULL AND c.email != '' AND c.is_deleted = 0 " +
            "GROUP BY LOWER(c.email) HAVING COUNT(c.id) > 1", nativeQuery = true)
    List<Object[]> findDuplicateEmailGroups();
}
