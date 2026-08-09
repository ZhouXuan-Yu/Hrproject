package com.hr.common.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色菜单权限映射，对齐 Flask ROLE_MENUS。
 * 菜单 ID: recruit-dashboard / recruit-demand / recruit-talent / recruit-interview / recruit-ai / recruit-config
 */
public final class RoleMenus {

    private RoleMenus() {
    }

    public static final List<String> MENUS = List.of(
            "recruit-dashboard",
            "recruit-demand",
            "recruit-talent",
            "recruit-interview",
            "recruit-ai",
            "recruit-config"
    );

    private static final Map<String, List<String>> ROLE_MENUS = Map.of(
            "admin", List.of("recruit-dashboard", "recruit-demand", "recruit-talent", "recruit-interview", "recruit-ai", "recruit-config"),
            "hr", List.of("recruit-dashboard", "recruit-demand", "recruit-talent", "recruit-interview"),
            "director", List.of("recruit-dashboard", "recruit-demand"),
            "dept_head", List.of("recruit-dashboard", "recruit-demand"),
            "interviewer", List.of("recruit-dashboard", "recruit-interview"),
            "temp_interviewer", List.of("recruit-dashboard", "recruit-interview"),
            "employee", List.of("recruit-dashboard", "recruit-demand"),
            "no_recruit", List.of()
    );

    public static List<String> getMenus(String roleCode) {
        return ROLE_MENUS.getOrDefault(roleCode, List.of());
    }

    /**
     * 计算角色着陆页（登录后跳转地址）。
     */
    public static String getRoleLanding(String roleCode) {
        List<String> menus = getMenus(roleCode);
        if (menus.contains("recruit-dashboard")) {
            return "/home";
        }
        return "/login";
    }

    public static boolean hasMenu(String roleCode, String menuId) {
        return getMenus(roleCode).contains(menuId);
    }
}
