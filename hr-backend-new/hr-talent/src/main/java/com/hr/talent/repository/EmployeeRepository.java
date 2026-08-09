package com.hr.talent.repository;

import com.hr.talent.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    List<Employee> findByIsDeleted(Integer isDeleted);

    Optional<Employee> findByUserId(Long userId);
}
