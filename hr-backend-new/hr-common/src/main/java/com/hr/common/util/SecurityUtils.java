package com.hr.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring SecurityContext 获取当前登录用户。
 * JwtAuthenticationFilter 认证成功后写入 LoginUser principal。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        return null;
    }

    public static Long getUserId() {
        LoginUser u = getCurrentUser();
        return u != null ? u.getUserId() : null;
    }

    public static String getRoleCode() {
        LoginUser u = getCurrentUser();
        return u != null ? u.getRoleCode() : null;
    }

    public static boolean hasAnyRole(String... roles) {
        String role = getRoleCode();
        if (role == null) {
            return false;
        }
        for (String r : roles) {
            if (role.equals(r)) {
                return true;
            }
        }
        return false;
    }
}
