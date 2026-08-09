package com.hr.auth.repository;

import com.hr.auth.entity.IamDept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IamDeptRepository extends JpaRepository<IamDept, Long> {

    List<IamDept> findByStatusAndIsDeleted(Integer status, Integer isDeleted);
}
