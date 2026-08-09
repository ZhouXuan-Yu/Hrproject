package com.hr.hire.repository;

import com.hr.hire.entity.HireEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface HireEventRepository extends JpaRepository<HireEvent, Long>, JpaSpecificationExecutor<HireEvent> {

    Optional<HireEvent> findFirstByOfferIdAndIsDeletedOrderByIdDesc(Long offerId, Integer isDeleted);
}
