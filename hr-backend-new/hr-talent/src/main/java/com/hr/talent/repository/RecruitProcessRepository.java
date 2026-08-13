package com.hr.talent.repository;

import com.hr.talent.entity.RecruitProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecruitProcessRepository extends JpaRepository<RecruitProcess, Long>, JpaSpecificationExecutor<RecruitProcess> {

    Optional<RecruitProcess> findFirstByCandidateIdAndDemandIdAndProcessStatusNotInAndIsDeleted(
            Long candidateId, Long demandId, List<Integer> statuses, Integer isDeleted);

    List<RecruitProcess> findByDemandIdAndIsDeletedOrderByIdDesc(Long demandId, Integer isDeleted);

    List<RecruitProcess> findByCandidateIdAndIsDeletedOrderByIdDesc(Long candidateId, Integer isDeleted);

    List<RecruitProcess> findByCandidateId(Long candidateId);

    List<RecruitProcess> findByCandidateIdInAndIsDeleted(Collection<Long> candidateIds, Integer isDeleted);

    List<RecruitProcess> findByDemandIdAndCandidateIdAndIsDeleted(Long demandId, Long candidateId, Integer isDeleted);
}
