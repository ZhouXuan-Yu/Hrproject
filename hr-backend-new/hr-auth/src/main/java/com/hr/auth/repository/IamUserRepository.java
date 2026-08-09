package com.hr.auth.repository;

import com.hr.auth.entity.IamUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface IamUserRepository extends JpaRepository<IamUser, Long>, JpaSpecificationExecutor<IamUser> {

    Optional<IamUser> findByUsername(String username);

    Optional<IamUser> findByEmployeeNo(String employeeNo);

    Optional<IamUser> findByUsernameOrEmployeeNo(String username, String employeeNo);

    List<IamUser> findByRoleCodeAndStatusAndIsDeleted(String roleCode, Integer status, Integer isDeleted);

    List<IamUser> findByStatusAndIsDeleted(Integer status, Integer isDeleted);
}
