package com.hr.demand.repository;

import com.hr.demand.entity.DemandApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DemandApprovalRepository extends JpaRepository<DemandApproval, Long> {

    List<DemandApproval> findByDemandIdOrderByApproveLevelAsc(Long demandId);
}
