-- V3__data_scope.sql — 数据范围（五档）角色默认配置 + 个人覆盖
-- 五档: all 全公司 / dept 本部门 / dept_and_self 本部门+指定给自己的 / self 仅自己 / none 无
-- 优先级: 个人(t_core_user.data_scope) > 角色(t_hr_role_data_scope) > 硬编码默认

CREATE TABLE IF NOT EXISTS `t_hr_role_data_scope` (
  `role_code` varchar(32) NOT NULL COMMENT '角色编码',
  `scope_type` varchar(32) NOT NULL COMMENT '数据范围: all/dept/dept_and_self/self/none',
  `enabled` int NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_t_hr_role_data_scope_role` (`role_code`),
  KEY `ix_t_hr_role_data_scope_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='角色数据范围配置';

-- 种子: 8 角色默认数据范围（对齐 architecture.md RBAC 矩阵，director 全公司）
INSERT INTO `t_hr_role_data_scope` (`role_code`, `scope_type`, `enabled`, `is_deleted`) VALUES
  ('admin', 'all', 1, 0),
  ('hr', 'all', 1, 0),
  ('director', 'all', 1, 0),
  ('dept_head', 'dept_and_self', 1, 0),
  ('employee', 'self', 1, 0),
  ('interviewer', 'self', 1, 0),
  ('temp_interviewer', 'self', 1, 0),
  ('no_recruit', 'none', 1, 0);

-- t_core_user 加个人数据范围覆盖列（NULL = 跟随角色）
ALTER TABLE `t_core_user`
  ADD COLUMN `data_scope` varchar(32) DEFAULT NULL COMMENT '个人数据范围覆盖: all/dept/dept_and_self/self/none，NULL 跟随角色';
