package com.hr.config.repository;

import com.hr.config.entity.RecruitMailAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecruitMailAccountRepository extends JpaRepository<RecruitMailAccount, Long> {

    List<RecruitMailAccount> findByIsDeletedOrderByIdAsc(Integer isDeleted);

    Optional<RecruitMailAccount> findByEmailAddress(String emailAddress);
}
