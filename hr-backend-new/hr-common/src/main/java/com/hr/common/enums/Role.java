package com.hr.common.enums;

/**
 * 系统角色枚举，对齐 Flask utils/enums.py 的 ROLES。
 */
public enum Role {
    ADMIN("admin", "管理员"),
    HR("hr", "HR 专员"),
    INTERVIEWER("interviewer", "面试官"),
    TEMP_INTERVIEWER("temp_interviewer", "临时面试官"),
    DEPT_HEAD("dept_head", "部门负责人"),
    DIRECTOR("director", "总监"),
    EMPLOYEE("employee", "基层员工"),
    NO_RECRUIT("no_recruit", "无权限员工");

    private final String code;
    private final String label;

    Role(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static Role fromCode(String code) {
        for (Role r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        return null;
    }
}
