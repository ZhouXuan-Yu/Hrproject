package com.hr.hire.repository;

import com.hr.hire.entity.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long>, JpaSpecificationExecutor<Offer> {

    Optional<Offer> findByOfferNoAndIsDeleted(String offerNo, Integer isDeleted);

    List<Offer> findByOfferStatusAndIsDeleted(Integer offerStatus, Integer isDeleted);
}
