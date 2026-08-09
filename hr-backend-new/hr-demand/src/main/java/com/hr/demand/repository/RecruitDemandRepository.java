package com.hr.demand.repository;

import com.hr.demand.entity.RecruitDemand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecruitDemandRepository extends JpaRepository<RecruitDemand, Long>, JpaSpecificationExecutor<RecruitDemand> {
}
