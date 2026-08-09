package com.hr.auth.repository;

import com.hr.auth.entity.IamPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IamPositionRepository extends JpaRepository<IamPosition, Long> {

    List<IamPosition> findByStatusAndIsDeleted(Integer status, Integer isDeleted);
}
