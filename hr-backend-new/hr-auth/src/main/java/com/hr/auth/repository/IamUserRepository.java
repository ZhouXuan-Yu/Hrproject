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

    /**
     * 按 user_id 取第一个有效用户。表内可能存在历史重复 user_id 行
     * （Flask 用 filter_by(...).first() 容错），此处显式限制取首行避免 findById 抛重复行异常。
     */
    @org.springframework.data.jpa.repository.Query(value =
            "SELECT * FROM t_core_user WHERE user_id = :userId AND status = 1 " +
            "AND (is_deleted = 0 OR is_deleted IS NULL) ORDER BY id LIMIT 1", nativeQuery = true)
    Optional<IamUser> findFirstActiveByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    List<IamUser> findByRoleCodeAndStatusAndIsDeleted(String roleCode, Integer status, Integer isDeleted);

    List<IamUser> findByStatusAndIsDeleted(Integer status, Integer isDeleted);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(MAX(u.userId), 0) FROM IamUser u")
    Long maxUserId();

    List<IamUser> findByRoleCodeInAndStatusAndIsDeleted(List<String> roleCodes, Integer status, Integer isDeleted);
}
