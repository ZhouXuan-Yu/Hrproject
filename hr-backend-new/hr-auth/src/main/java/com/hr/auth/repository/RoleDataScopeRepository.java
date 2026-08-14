package com.hr.auth.repository;

import com.hr.auth.entity.RoleDataScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleDataScopeRepository extends JpaRepository<RoleDataScope, Long> {

    Optional<RoleDataScope> findFirstByRoleCodeAndEnabledAndIsDeleted(
            String roleCode, Integer enabled, Integer isDeleted);
}
