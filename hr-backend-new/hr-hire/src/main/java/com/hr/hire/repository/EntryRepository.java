package com.hr.hire.repository;

import com.hr.hire.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EntryRepository extends JpaRepository<Entry, Long>, JpaSpecificationExecutor<Entry> {

    Optional<Entry> findByEntryNoAndIsDeleted(String entryNo, Integer isDeleted);
}
