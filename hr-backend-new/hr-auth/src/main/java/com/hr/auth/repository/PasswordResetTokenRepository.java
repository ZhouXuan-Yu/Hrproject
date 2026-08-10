package com.hr.auth.repository;

import com.hr.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findFirstByUserIdAndTokenAndStatusAndIsDeletedOrderByIdDesc(
            Long userId, String token, String status, Integer isDeleted);

    List<PasswordResetToken> findByUserIdAndStatusAndIsDeleted(
            Long userId, String status, Integer isDeleted);

    @Modifying
    @Query("update PasswordResetToken t set t.status = 'expired', t.updatedAt = :now " +
            "where t.userId = :userId and t.status = 'pending' and t.isDeleted = 0")
    int expirePendingTokens(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    Optional<PasswordResetToken> findFirstByTargetAndTokenAndStatusAndIsDeletedOrderByIdDesc(
            String target, String token, String status, Integer isDeleted);
}
