package com.hr.common.aspect;

import com.hr.common.annotation.RequireRole;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * RBAC 角色校验切面。
 * <p>
 * Spring AOP 不支持在 {@code ||} 分支中绑定切点参数，因此拆成两个切点：
 * {@code @within} 匹配类级注解，{@code @annotation} 匹配方法级注解。
 * 方法级注解优先于类级注解（方法可收窄权限，例如 admin 用户管理接口）。
 */
@Aspect
@Component
public class RequireRoleAspect {

    @Before("@within(requireRole)")
    public void checkClassLevel(JoinPoint jp, RequireRole requireRole) {
        checkEffectiveRole(jp, requireRole);
    }

    @Before("@annotation(requireRole)")
    public void checkMethodLevel(JoinPoint jp, RequireRole requireRole) {
        checkEffectiveRole(jp, requireRole);
    }

    private void checkEffectiveRole(JoinPoint jp, RequireRole methodAnnotation) {
        RequireRole effective = methodAnnotation;
        if (effective == null) {
            // 仅类级注解时，从目标类解析
            MethodSignature sig = (MethodSignature) jp.getSignature();
            effective = sig.getMethod().getAnnotation(RequireRole.class);
        }
        if (effective == null) {
            effective = jp.getTarget().getClass().getAnnotation(RequireRole.class);
        }
        if (effective == null) {
            return;
        }
        check(effective);
    }

    private void check(RequireRole requireRole) {
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
