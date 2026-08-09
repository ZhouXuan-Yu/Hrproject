package com.hr.auth.controller;

import com.hr.auth.entity.IamUser;
import com.hr.auth.repository.IamUserRepository;
import com.hr.common.annotation.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理接口（仅 admin），对齐 Flask /api/auth/users。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final IamUserRepository userRepository;

    @GetMapping("/users")
    @RequireRole({"admin"})
    public Map<String, Object> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {

        Specification<IamUser> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("realName"), like),
                        cb.like(root.get("username"), like),
                        cb.like(root.get("employeeNo"), like)));
            }
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(root.get("roleCode"), role));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<IamUser> result = userRepository.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), pageSize, Sort.by(Sort.Direction.DESC, "userId")));

        List<Map<String, Object>> users = result.getContent().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getUserId());
            m.put("username", u.getUsername());
            m.put("employeeNo", u.getEmployeeNo());
            m.put("realName", u.getRealName());
            m.put("roleCode", u.getRoleCode());
            m.put("deptId", u.getDeptId());
            m.put("status", u.getStatus());
            m.put("mobile", u.getMobile());
            return m;
        }).toList();

        // 对齐 Flask 分页结构：{ data:[...], total:N, page:N, pageSize:N }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", users);
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }
}
