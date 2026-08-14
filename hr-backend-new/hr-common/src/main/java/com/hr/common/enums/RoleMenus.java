package com.hr.common.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色菜单权限映射，对齐前端 useAuth.js ROLE_MENUS。
 * 菜单 ID: home / recruit-dashboard / recruit-demand / recruit-talent / recruit-interview / recruit-ai / recruit-config / recruit-accounts
 */
public final class RoleMenus {

    private RoleMenus() {
    }

    public static final List<String> MENUS = List.of(
            "home",
            "recruit-dashboard",
            "recruit-demand",
            "recruit-talent",
            "recruit-interview",
            "recruit-ai",
            "recruit-config",
            "recruit-accounts"
    );

    private static final Map<String, List<String>> ROLE_MENUS = Map.of(
            "admin", List.of("home", "recruit-dashboard", "recruit-demand", "recruit-talent", "recruit-interview", "recruit-ai", "recruit-config", "recruit-accounts"),
            "hr", List.of("home", "recruit-dashboard", "recruit-demand", "recruit-talent", "recruit-interview", "recruit-ai"),
            "director", List.of("home", "recruit-dashboard", "recruit-demand"),
            "dept_head", List.of("recruit-demand", "recruit-talent", "recruit-interview"),
            "interviewer", List.of("recruit-interview", "recruit-talent"),
            "temp_interviewer", List.of("recruit-interview"),
            "employee", List.of("recruit-demand"),
            "no_recruit", List.of()
    );

    public static List<String> getMenus(String roleCode) {
        return ROLE_MENUS.getOrDefault(roleCode, List.of());
    }

    private static final Map<String, String> MENU_TO_ROUTE = Map.of(
            "home", "/home",
            "recruit-dashboard", "/recruit-dashboard",
            "recruit-demand", "/recruit-demand",
            "recruit-talent", "/recruit-talent",
            "recruit-interview", "/recruit-interview",
            "recruit-ai", "/recruit-ai",
            "recruit-config", "/recruit-config",
            "recruit-accounts", "/recruit-accounts"
    );

    /**
     * 计算角色着陆页（登录后跳转地址）：跳该角色第一个菜单对应的路由，与前端 useAuth.js 一致。
     */
    public static String getRoleLanding(String roleCode) {
        List<String> menus = getMenus(roleCode);
        if (menus.isEmpty()) {
            return "/login";
        }
        return MENU_TO_ROUTE.getOrDefault(menus.get(0), "/login");
    }

    public static boolean hasMenu(String roleCode, String menuId) {
        return getMenus(roleCode).contains(menuId);
    }
}
