package com.hr.auth.repository;

import com.hr.auth.entity.IamPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IamPositionRepository extends JpaRepository<IamPosition, Long> {

    List<IamPosition> findByStatusAndIsDeleted(Integer status, Integer isDeleted);

    /** 按岗位名查找启用的岗位 */
    List<IamPosition> findByPositionNameAndStatusAndIsDeleted(String positionName, Integer status, Integer isDeleted);

    /** 按 position_no 查岗位 */
    java.util.Optional<IamPosition> findByPositionNoAndIsDeleted(String positionNo, Integer isDeleted);

    /** 按 status 过滤（含已删除，?all=1） */
    List<IamPosition> findByIsDeleted(Integer isDeleted);
}
