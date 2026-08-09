package com.hr.common.aspect;

import com.hr.common.annotation.RequireRole;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.SecurityUtils;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * RBAC 方法级校验切面，处理 @RequireRole 注解。
 */
@Aspect
@Component
public class RequireRoleAspect {

    @Before("@annotation(requireRole)")
    public void check(RequireRole requireRole) {
        String role = SecurityUtils.getRoleCode();
        if (role == null) {
            throw BusinessException.unauthorized();
        }
        // allowAny 放行
        for (String any : requireRole.allowAny()) {
            if ("*".equals(any) || role.equals(any)) {
                return;
            }
        }
        String[] allowed = requireRole.value();
        for (String r : allowed) {
            if (role.equals(r)) {
                return;
            }
        }
        throw BusinessException.forbidden();
    }
}
