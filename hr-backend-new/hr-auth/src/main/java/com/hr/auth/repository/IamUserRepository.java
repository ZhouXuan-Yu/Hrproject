package com.hr.auth.repository;

import com.hr.auth.entity.IamUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface IamUserRepository extends JpaRepository<IamUser, Long>, JpaSpecificationExecutor<IamUser> {

    Optional<IamUser> findByUsername(String username);

    Optional<IamUser> findByEmployeeNo(String employeeNo);

    boolean existsByMobileAndIsDeleted(String mobile, Integer isDeleted);

    boolean existsByEmailAndIsDeleted(String email, Integer isDeleted);

    /** 按真实姓名查第一个有效用户（用于获取 feishu_open_id）。 */
    Optional<IamUser> findFirstByRealNameAndStatusAndIsDeleted(String realName, Integer status, Integer isDeleted);

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

    @org.springframework.data.jpa.repository.Query(value =
            "SELECT MAX(u.employee_no) FROM t_core_user u WHERE u.employee_no LIKE :prefix AND u.is_deleted = 0",
            nativeQuery = true)
    String maxEmployeeNoLike(@org.springframework.data.repository.query.Param("prefix") String prefix);

    List<IamUser> findByRoleCodeInAndStatusAndIsDeleted(List<String> roleCodes, Integer status, Integer isDeleted);

    /** 按部门统计在职员工数（status=1, is_deleted=0），返回 [deptId, count] */
    @org.springframework.data.jpa.repository.Query(value =
            "SELECT dept_id, COUNT(*) FROM t_core_user WHERE status = 1 " +
            "AND (is_deleted = 0 OR is_deleted IS NULL) AND dept_id IS NOT NULL " +
            "GROUP BY dept_id", nativeQuery = true)
    List<Object[]> countUsersByDept();
}
