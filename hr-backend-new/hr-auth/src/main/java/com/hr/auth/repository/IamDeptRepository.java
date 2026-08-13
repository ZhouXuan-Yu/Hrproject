package com.hr.auth.repository;

import com.hr.auth.entity.IamDept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IamDeptRepository extends JpaRepository<IamDept, Long> {

    List<IamDept> findByStatusAndIsDeleted(Integer status, Integer isDeleted);

    /** 所有未删除的部门（?all=1 时用，包含停用的） */
    List<IamDept> findByIsDeleted(Integer isDeleted);

    /** 按部门名查找启用的部门 */
    List<IamDept> findByDeptNameAndStatusAndIsDeleted(String deptName, Integer status, Integer isDeleted);
}
