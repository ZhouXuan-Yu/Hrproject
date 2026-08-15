package com.hr.hire.repository;

import com.hr.hire.entity.OfferRemindLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OfferRemindLogRepository extends JpaRepository<OfferRemindLog, Long> {

    Optional<OfferRemindLog> findFirstByOfferIdAndRemindTypeOrderByIdDesc(Long offerId, String remindType);
}
