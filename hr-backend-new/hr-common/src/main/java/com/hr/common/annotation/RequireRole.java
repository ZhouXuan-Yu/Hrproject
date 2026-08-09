package com.hr.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级/类级 RBAC 角色校验注解，等价于 Flask 的 require_role 装饰器。
 * 若用户角色不在 allowedRoles 中则抛出 FORBIDDEN 异常。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    String[] value() default {};

    /**
     * 当角色在 allowAny 中时，放行任意角色。
     */
    String[] allowAny() default {};

    String message() default "无权限访问";
}
