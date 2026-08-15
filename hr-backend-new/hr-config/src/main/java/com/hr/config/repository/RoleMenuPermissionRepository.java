package com.hr.config.repository;

import com.hr.config.entity.RoleMenuPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleMenuPermissionRepository extends JpaRepository<RoleMenuPermission, Long> {

    List<RoleMenuPermission> findByIsDeletedOrderByRoleCodeAsc(Integer isDeleted);

    List<RoleMenuPermission> findByRoleCodeAndEnabledAndIsDeletedOrderByIdAsc(
            String roleCode, Integer enabled, Integer isDeleted);
}
