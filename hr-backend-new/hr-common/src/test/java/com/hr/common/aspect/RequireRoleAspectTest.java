package com.hr.common.aspect;

import com.hr.common.annotation.RequireRole;
import com.hr.common.exception.BusinessException;
import com.hr.common.util.LoginUser;
import com.hr.common.util.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 RequireRoleAspect 对类级 / 方法级注解与 allowAny 的处理。
 */
class RequireRoleAspectTest {

    private final RequireRoleAspect aspect = new RequireRoleAspect();

    @BeforeEach
    void setup() {
        login("hr", 1L, 10L);
    }

    @AfterEach
    void teardown() {
        SecurityContextHolder.clearContext();
    }

    private void login(String role, Long userId, Long deptId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        LoginUser.of(userId, role, 1L, "u", "测试", deptId),
                        null, List.of()));
    }

    private JoinPoint joinPointFor(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Method method = clazz.getMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getSignature()).thenReturn(signature);
        when(jp.getTarget()).thenReturn(mock(clazz));
        return jp;
    }

    // ── 类级注解（配置类控制器场景） ─────────────────────────────

    @RequireRole({"admin", "hr"})
    static class ClassAnnotated {
        public void list() {
        }
    }

    @Test
    void classLevel_hrAllowed() throws Exception {
        aspect.checkClassLevel(joinPointFor(ClassAnnotated.class, "list"),
                ClassAnnotated.class.getAnnotation(RequireRole.class));
        // 不抛异常即通过
    }

    @Test
    void classLevel_employeeForbidden() throws Exception {
        login("employee", 2L, 10L);
        BusinessException e = assertThrows(BusinessException.class,
                () -> aspect.checkClassLevel(joinPointFor(ClassAnnotated.class, "list"),
                        ClassAnnotated.class.getAnnotation(RequireRole.class)));
        assertEquals("FORBIDDEN", e.getCode());
        assertEquals(403, e.getStatus());
    }

    // ── 方法级注解收窄权限 ─────────────────────────────────────

    static class MethodAnnotated {
        @RequireRole({"admin"})
        public void delete() {
        }
    }

    @Test
    void methodLevel_adminAllowed() throws Exception {
        login("admin", 1L, 1L);
        Method m = MethodAnnotated.class.getMethod("delete");
        aspect.checkMethodLevel(joinPointFor(MethodAnnotated.class, "delete"), m.getAnnotation(RequireRole.class));
    }

    @Test
    void methodLevel_hrForbidden() throws Exception {
        Method m = MethodAnnotated.class.getMethod("delete");
        BusinessException e = assertThrows(BusinessException.class,
                () -> aspect.checkMethodLevel(joinPointFor(MethodAnnotated.class, "delete"),
                        m.getAnnotation(RequireRole.class)));
        assertEquals("FORBIDDEN", e.getCode());
    }

    // ── allowAny 放行 ──────────────────────────────────────────

    @RequireRole(allowAny = {"*"})
    static class AnyAnnotated {
        public void any() {
        }
    }

    @Test
    void allowAny_wildcardPassesAllRoles() throws Exception {
        login("interviewer", 3L, null);
        aspect.checkClassLevel(joinPointFor(AnyAnnotated.class, "any"),
                AnyAnnotated.class.getAnnotation(RequireRole.class));
    }

    // ── 未登录 ─────────────────────────────────────────────────

    @Test
    void unauthenticated_throwsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();
        BusinessException e = assertThrows(BusinessException.class,
                () -> aspect.checkClassLevel(joinPointFor(ClassAnnotated.class, "list"),
                        ClassAnnotated.class.getAnnotation(RequireRole.class)));
        assertEquals("UNAUTHORIZED", e.getCode());
        assertEquals(401, e.getStatus());
    }

    // ── SecurityUtils 数据权限上下文 ───────────────────────────

    @Test
    void securityUtils_readsDeptFromLoginUser() {
        assertEquals(Long.valueOf(10L), SecurityUtils.getCurrentUser().getDeptId());
        assertEquals("hr", SecurityUtils.getRoleCode());
        assertEquals(Long.valueOf(1L), SecurityUtils.getUserId());
    }
}
