package com.hr.auth.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 登录响应，对齐 Flask login() 的 body 结构。
 */
@Data
public class LoginResponse {
    private String token;
    private UserInfo user;
    private List<String> menus;

    @Data
    public static class UserInfo {
        private String id;
        private String name;
        private String role;
        private String avatar;
        private Boolean mustChangePassword;
    }

    public static LoginResponse of(String token, Map<String, Object> user, List<String> menus) {
        LoginResponse r = new LoginResponse();
        r.setToken(token);
        UserInfo info = new UserInfo();
        info.setId(String.valueOf(user.get("id")));
        info.setName(String.valueOf(user.getOrDefault("name", "")));
        info.setRole(String.valueOf(user.getOrDefault("role", "")));
        info.setAvatar((String) user.get("avatar"));
        info.setMustChangePassword(Boolean.TRUE.equals(user.get("mustChangePassword")));
        r.setUser(info);
        r.setMenus(menus);
        return r;
    }
}
