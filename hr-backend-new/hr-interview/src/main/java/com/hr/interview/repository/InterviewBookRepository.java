package com.hr.interview.repository;

import com.hr.interview.entity.InterviewBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InterviewBookRepository extends JpaRepository<InterviewBook, Long>, JpaSpecificationExecutor<InterviewBook> {
}
