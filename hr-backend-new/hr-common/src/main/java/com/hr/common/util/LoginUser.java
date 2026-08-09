package com.hr.common.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户上下文（从 JWT 解析后写入）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {
    private Long userId;
    private String roleCode;
    private Long tenantId;
    private String username;
    private String realName;
    private Long deptId;

    public static LoginUser of(Long userId, String roleCode, Long tenantId, String username, String realName, Long deptId) {
        return new LoginUser(userId, roleCode, tenantId, username, realName, deptId);
    }
}
