-- V2__system_config.sql — key-value 配置表 + 缺少的索引
CREATE TABLE IF NOT EXISTS `t_hr_system_config` (
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` varchar(4096) DEFAULT NULL COMMENT '配置值（JSON或纯文本）',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
