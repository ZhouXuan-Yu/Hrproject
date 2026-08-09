package com.hr.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解，等价于 Flask 的 data_scope.py 过滤逻辑。
 * 在 Service/Repository 层通过 AOP 自动注入 WHERE 条件。
 * <p>
 * 规则（与现有 data_scope.py 一致）：
 * <ul>
 *   <li>admin / hr / director: 不过滤</li>
 *   <li>dept_head: 仅本部门数据</li>
 *   <li>employee: 仅本人创建的数据</li>
 *   <li>interviewer: 仅分配的面试场次</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {
    /**
     * 部门字段名（实体属性名），如 "deptId"。
     */
    String deptField() default "deptId";

    /**
     * 创建人字段名（实体属性名），如 "creatorId"。
     */
    String creatorField() default "creatorId";
}
