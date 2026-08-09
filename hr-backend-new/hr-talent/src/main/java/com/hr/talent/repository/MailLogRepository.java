package com.hr.talent.repository;

import com.hr.talent.entity.MailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MailLogRepository extends JpaRepository<MailLog, Long> {

    List<MailLog> findTop50ByIsDeletedOrderByIdDesc(Integer isDeleted);
}
