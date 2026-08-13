-- V1__baseline.sql — 智能招聘系统完整DDL
-- Auto-generated from existing database

-- Table: files
CREATE TABLE `files` (
  `file_name` varchar(256) NOT NULL COMMENT '原始文件名',
  `file_url` varchar(512) NOT NULL COMMENT '文件存储地址',
  `file_extension` varchar(16) DEFAULT NULL COMMENT '后缀 pdf/doc/png',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小字节',
  `biz_type` varchar(64) DEFAULT NULL COMMENT '业务类型 resume/offer/audio',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb3;

-- Table: t_core_dept
CREATE TABLE `t_core_dept` (
  `dept_id` bigint NOT NULL COMMENT '组织ID',
  `dept_name` varchar(100) NOT NULL COMMENT '组织名称',
  `parent_dept_id` bigint DEFAULT NULL COMMENT '上级组织ID',
  `dept_path` varchar(512) DEFAULT NULL COMMENT '组织路径',
  `sort_num` int NOT NULL COMMENT '排序号',
  `status` int NOT NULL COMMENT '状态: 1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb3;

-- Table: t_core_position
CREATE TABLE `t_core_position` (
  `position_id` bigint NOT NULL COMMENT '岗位ID',
  `position_name` varchar(100) NOT NULL COMMENT '岗位名称',
  `position_no` varchar(32) DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL COMMENT '所属组织ID',
  `status` int NOT NULL COMMENT '状态: 1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_position_no` (`position_no`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb3;

-- Table: t_core_user
CREATE TABLE `t_core_user` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(64) NOT NULL COMMENT '登录用户名',
  `real_name` varchar(64) NOT NULL COMMENT '真实姓名',
  `dept_id` bigint DEFAULT NULL COMMENT '所属组织ID',
  `position_id` bigint DEFAULT NULL COMMENT '岗位ID',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机号',
  `feishu_open_id` varchar(64) DEFAULT NULL COMMENT '飞书open_id',
  `status` int NOT NULL COMMENT '状态: 1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  `password_hash` varchar(256) DEFAULT NULL COMMENT '密码哈希',
  `must_change_password` int NOT NULL DEFAULT '1',
  `password_updated_at` datetime DEFAULT NULL,
  `employee_no` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_employee_no` (`employee_no`),
  KEY `idx_t_core_user_dept_id` (`dept_id`),
  KEY `idx_t_core_user_position_id` (`position_id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_ai_knowledge_base
CREATE TABLE `t_hr_ai_knowledge_base` (
  `company_name` varchar(128) NOT NULL,
  `industry` varchar(128) DEFAULT NULL,
  `website` varchar(256) DEFAULT NULL,
  `company_profile` text,
  `hiring_principles` text,
  `ai_context` text,
  `status` int NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_api_key
CREATE TABLE `t_hr_api_key` (
  `key_name` varchar(64) NOT NULL COMMENT '密钥标识: deepseek/feishu/dify',
  `value_encrypted` varchar(512) NOT NULL COMMENT 'AES-256-GCM 加密后的值',
  `display_label` varchar(64) DEFAULT NULL COMMENT '前端显示名称',
  `status` int NOT NULL COMMENT '1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `key_name` (`key_name`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_approval_identity
CREATE TABLE `t_hr_approval_identity` (
  `approve_level` int NOT NULL COMMENT '审批层级 1部门负责人/2HR/3高管',
  `identity_code` varchar(32) NOT NULL COMMENT '身份编码 dept_head/hr/executive',
  `identity_name` varchar(64) NOT NULL COMMENT '身份名称',
  `role_code` varchar(32) NOT NULL COMMENT '允许审批的登录角色',
  `user_id` bigint DEFAULT NULL COMMENT '可选指定审批人，为空表示该角色均可审批',
  `status` int NOT NULL COMMENT '1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_audit_log
CREATE TABLE `t_hr_audit_log` (
  `operator_name` varchar(64) NOT NULL COMMENT '操作人姓名',
  `module` varchar(32) NOT NULL COMMENT '模块: demand/interview/candidate/mail/config',
  `action` varchar(64) NOT NULL COMMENT '动作描述',
  `detail` varchar(512) DEFAULT NULL COMMENT '详情',
  `operate_time` datetime NOT NULL COMMENT '操作时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=339 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_candidate
CREATE TABLE `t_hr_candidate` (
  `candidate_no` varchar(32) NOT NULL COMMENT '候选人编号',
  `candidate_name` varchar(30) NOT NULL COMMENT '候选人姓名',
  `mobile` varchar(20) DEFAULT NULL COMMENT '手机号(加密存储)',
  `mobile_hash` varchar(64) DEFAULT NULL COMMENT 'SHA256用于去重',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `static_ability_score` decimal(4,1) DEFAULT NULL COMMENT '静态画像分(0-100)',
  `edu_level` int DEFAULT NULL COMMENT '学历: 1大专 2本科 3硕士 4博士',
  `school_level` int DEFAULT NULL COMMENT '院校: 1普通 2-211 3-985 4-C9',
  `work_years` int DEFAULT NULL COMMENT '工作年限',
  `big_company_flag` int NOT NULL COMMENT '大厂经历: 0否 1是',
  `cert_count` int NOT NULL COMMENT '证书数量',
  `source_channel` varchar(32) DEFAULT NULL COMMENT '来源渠道 邮箱/Boss/猎聘/内推',
  `status` varchar(16) NOT NULL COMMENT 'available/locked/reserve/archived',
  `black_flag` int NOT NULL COMMENT '0正常 1黑名单',
  `black_type` int DEFAULT NULL COMMENT '1简历造假 2多次爽约 3薪资虚高',
  `black_add_at` datetime DEFAULT NULL COMMENT '拉黑时间',
  `note` varchar(512) DEFAULT NULL COMMENT 'HR备注',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `candidate_no` (`candidate_no`),
  UNIQUE KEY `mobile_hash` (`mobile_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_candidate_tag_rel
CREATE TABLE `t_hr_candidate_tag_rel` (
  `candidate_id` bigint NOT NULL COMMENT '候选人ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `tag_source` int NOT NULL COMMENT '1系统自动 2HR手动 3JD自动匹配',
  `valid_end` date DEFAULT NULL COMMENT '证书/技能过期时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_candidate_tag_rel_candidate_id` (`candidate_id`),
  KEY `idx_t_hr_candidate_tag_rel_tag_id` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_chat_log
CREATE TABLE `t_hr_chat_log` (
  `resume_id` bigint DEFAULT NULL COMMENT '简历ID',
  `demand_id` bigint DEFAULT NULL COMMENT '需求ID',
  `chat_type` int NOT NULL COMMENT '1AI自动对话 2人工HR',
  `chat_content` text NOT NULL COMMENT '对话内容',
  `operate_time` datetime NOT NULL COMMENT '对话发生时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_company_role_policy
CREATE TABLE `t_hr_company_role_policy` (
  `company_id` varchar(64) NOT NULL COMMENT '公司/租户标识',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码',
  `permissions_json` text COMMENT '权限策略 JSON',
  `status` int NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_demand_approval
CREATE TABLE `t_hr_demand_approval` (
  `demand_id` bigint NOT NULL COMMENT '关联需求ID',
  `approve_user_id` bigint DEFAULT NULL COMMENT '审批人用户ID',
  `approve_level` int DEFAULT NULL COMMENT '审批层级 1/2/3',
  `approve_result` int NOT NULL COMMENT '1待审批 2通过 3驳回',
  `approve_opinion` varchar(512) DEFAULT NULL COMMENT '审批意见',
  `approve_time` datetime DEFAULT NULL COMMENT '审批操作时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_demand_approval_demand_id` (`demand_id`)
) ENGINE=InnoDB AUTO_INCREMENT=152 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_dept_hc
CREATE TABLE `t_hr_dept_hc` (
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  `plan_year` int NOT NULL COMMENT '编制年份',
  `total_headcount` int NOT NULL COMMENT '年度总编制',
  `used_headcount` int NOT NULL COMMENT '已占用',
  `available_headcount` int NOT NULL COMMENT '剩余可用',
  `operation_json` json DEFAULT NULL COMMENT '编制变动流水快照',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_employee
CREATE TABLE `t_hr_employee` (
  `user_id` bigint NOT NULL COMMENT '绑定登录账号ID',
  `dept_id` bigint DEFAULT NULL COMMENT '当前部门ID',
  `position_id` bigint DEFAULT NULL COMMENT '当前岗位ID',
  `work_years` int DEFAULT NULL COMMENT '总工作年限',
  `perf_score` decimal(3,1) DEFAULT NULL COMMENT '绩效分',
  `last_promote_date` date DEFAULT NULL COMMENT '上次晋升时间',
  `can_transfer` int NOT NULL COMMENT '0不可调 1可调',
  `compositive_score` decimal(4,1) DEFAULT NULL COMMENT '综合评估分',
  `transfer_restrict_reason` varchar(256) DEFAULT NULL COMMENT '不可调岗原因',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_t_hr_employee_dept_id` (`dept_id`),
  KEY `idx_t_hr_employee_position_id` (`position_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_employee_tag_rel
CREATE TABLE `t_hr_employee_tag_rel` (
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `tag_source` int NOT NULL COMMENT '1档案同步 2HR手动 3内部匹配生成',
  `tag_related_score` decimal(3,1) DEFAULT NULL COMMENT '标签附带绩效分',
  `valid_end` date DEFAULT NULL COMMENT '荣誉/临时标签过期时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_employee_tag_rel_employee_id` (`employee_id`),
  KEY `idx_t_hr_employee_tag_rel_tag_id` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_entry
CREATE TABLE `t_hr_entry` (
  `entry_no` varchar(32) NOT NULL COMMENT '入职编号',
  `event_id` bigint NOT NULL COMMENT '录用事件ID',
  `resume_id` bigint NOT NULL COMMENT '简历ID',
  `dept_id` bigint NOT NULL COMMENT '入职部门ID',
  `position_id` bigint NOT NULL COMMENT '入职岗位ID',
  `entry_date` date NOT NULL COMMENT '实际入职日期',
  `checklist_json` json DEFAULT NULL COMMENT '入职待办/转正记录快照',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `entry_no` (`entry_no`),
  KEY `idx_t_hr_entry_event_id` (`event_id`),
  KEY `idx_t_hr_entry_resume_id` (`resume_id`),
  KEY `idx_t_hr_entry_dept_id` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_hire_event
CREATE TABLE `t_hr_hire_event` (
  `event_no` varchar(32) NOT NULL COMMENT '事件编号',
  `process_id` bigint DEFAULT NULL COMMENT '外部流程ID，内调NULL',
  `employee_id` bigint DEFAULT NULL COMMENT '内部员工ID，外招NULL',
  `offer_id` bigint DEFAULT NULL COMMENT 'Offer ID，内调NULL',
  `hire_type` int NOT NULL COMMENT '1外部Offer录用 2内部调岗 3离职返聘',
  `event_status` int NOT NULL COMMENT '0待办理 1已生成入职单 2作废',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_no` (`event_no`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_internal_match_log
CREATE TABLE `t_hr_internal_match_log` (
  `match_no` varchar(32) NOT NULL COMMENT '匹配流水编号',
  `demand_id` bigint NOT NULL COMMENT '需求ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `match_score` decimal(4,1) DEFAULT NULL COMMENT '内部匹配分',
  `match_status` int NOT NULL COMMENT '10待确认 20已调配 30忽略',
  `operator_user_id` bigint DEFAULT NULL COMMENT '操作HR',
  `matched_at` datetime NOT NULL COMMENT '匹配时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `match_no` (`match_no`),
  KEY `idx_t_hr_internal_match_log_demand_id` (`demand_id`),
  KEY `idx_t_hr_internal_match_log_employee_id` (`employee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_interview_book
CREATE TABLE `t_hr_interview_book` (
  `demand_id` bigint NOT NULL COMMENT '需求ID',
  `resume_id` bigint NOT NULL COMMENT '简历ID',
  `process_id` bigint NOT NULL COMMENT '流程ID',
  `slot_id` bigint NOT NULL COMMENT '时段ID',
  `interview_round` int NOT NULL COMMENT '1一面 2二面',
  `interview_type` int NOT NULL COMMENT '1飞书 2腾讯会议 3其他线上 4线下',
  `meeting_code` varchar(32) DEFAULT NULL COMMENT '会议号码',
  `meeting_pwd` varchar(16) DEFAULT NULL COMMENT '入会密码',
  `meeting_url` varchar(500) DEFAULT NULL COMMENT '视频会议链接',
  `address` varchar(200) DEFAULT NULL COMMENT '线下地址',
  `book_time` datetime NOT NULL COMMENT '预约操作时间',
  `invite_json` json DEFAULT NULL COMMENT '邀约记录',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_interview_book_demand_id` (`demand_id`),
  KEY `idx_t_hr_interview_book_resume_id` (`resume_id`),
  KEY `idx_t_hr_interview_book_process_id` (`process_id`),
  KEY `idx_t_hr_interview_book_slot_id` (`slot_id`)
) ENGINE=InnoDB AUTO_INCREMENT=68 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_interview_record
CREATE TABLE `t_hr_interview_record` (
  `book_id` bigint NOT NULL COMMENT '关联预约单ID',
  `process_id` bigint NOT NULL COMMENT '流程ID',
  `interviewer_ids` json NOT NULL COMMENT '本场所有面试官ID',
  `submit_interviewer_id` bigint NOT NULL COMMENT '提交评价人ID',
  `is_arrive` int NOT NULL COMMENT '0未到 1已到',
  `interview_result` int NOT NULL COMMENT '0不通过 1通过',
  `evaluate_text` text COMMENT '文字评价',
  `score_json` json DEFAULT NULL COMMENT '多维度分项打分',
  `audio_url` varchar(255) DEFAULT NULL COMMENT '录音文件地址',
  `end_time` datetime DEFAULT NULL COMMENT '面试结束时间',
  `feishu_memo_url` varchar(512) DEFAULT NULL COMMENT '飞书妙记链接',
  `highlight_json` json DEFAULT NULL COMMENT 'AI关键问答',
  `ai_draft_json` json DEFAULT NULL COMMENT 'AI评价草稿',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_interview_record_book_id` (`book_id`),
  KEY `idx_t_hr_interview_record_process_id` (`process_id`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_interview_slot
CREATE TABLE `t_hr_interview_slot` (
  `interviewer_id` bigint NOT NULL COMMENT '面试官用户ID',
  `demand_id` bigint DEFAULT NULL COMMENT '绑定需求ID NULL=通用',
  `start_dt` datetime NOT NULL COMMENT '时段开始',
  `end_dt` datetime NOT NULL COMMENT '时段结束',
  `is_booked` int NOT NULL COMMENT '0空闲 1占用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_interview_slot_interviewer_id` (`interviewer_id`),
  KEY `idx_t_hr_interview_slot_demand_id` (`demand_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_mail_log
CREATE TABLE `t_hr_mail_log` (
  `sender_account_id` bigint DEFAULT NULL COMMENT '发件邮箱账号ID',
  `sender_email` varchar(128) NOT NULL COMMENT '发件邮箱地址',
  `recipient` varchar(256) NOT NULL COMMENT '收件邮箱地址',
  `subject` varchar(256) NOT NULL COMMENT '邮件主题',
  `mail_type` varchar(32) NOT NULL COMMENT 'invite面试邀请/offer录用/entry入职包/test测试/other其他',
  `status` int NOT NULL COMMENT '1成功 0失败',
  `error_msg` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_notify_template
CREATE TABLE `t_hr_notify_template` (
  `template_name` varchar(128) NOT NULL COMMENT '模板名称',
  `template_type` varchar(32) NOT NULL COMMENT '类型: interview/offer/reject/remind',
  `send_method` varchar(64) DEFAULT NULL COMMENT '发送方式: 飞书/短信/邮件/组合',
  `subject` varchar(256) DEFAULT NULL COMMENT '消息标题',
  `body` text COMMENT '模板正文',
  `status` int NOT NULL COMMENT '1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_offer
CREATE TABLE `t_hr_offer` (
  `offer_no` varchar(32) NOT NULL COMMENT 'Offer编号',
  `resume_id` bigint NOT NULL COMMENT '简历ID',
  `process_id` bigint NOT NULL COMMENT '流程ID',
  `demand_id` bigint NOT NULL COMMENT '需求ID',
  `last_interview_id` bigint DEFAULT NULL COMMENT '最后一面面试记录ID',
  `offer_content` text COMMENT 'Offer正文',
  `salary_json` json DEFAULT NULL COMMENT '薪资/补贴/试用期结构化数据',
  `valid_deadline` datetime NOT NULL COMMENT '截止时间',
  `offer_status` int NOT NULL COMMENT '0草稿 1已发送 2已接受 3已拒绝 4已过期',
  `send_user_id` bigint NOT NULL COMMENT '发放HR',
  `send_time` datetime NOT NULL COMMENT '发放时间',
  `offer_file_id` bigint DEFAULT NULL COMMENT 'Offer附件ID',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `offer_no` (`offer_no`),
  KEY `idx_t_hr_offer_resume_id` (`resume_id`),
  KEY `idx_t_hr_offer_process_id` (`process_id`),
  KEY `idx_t_hr_offer_demand_id` (`demand_id`),
  KEY `idx_t_hr_offer_last_interview_id` (`last_interview_id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_offer_remind_log
CREATE TABLE `t_hr_offer_remind_log` (
  `offer_id` bigint NOT NULL COMMENT 'Offer主键ID',
  `offer_no` varchar(32) NOT NULL COMMENT 'Offer编号',
  `remind_type` varchar(16) NOT NULL COMMENT 'countdown倒计时提醒',
  `days_left` int NOT NULL COMMENT '发送时剩余天数',
  `sent_to` varchar(128) DEFAULT NULL COMMENT '收件邮箱',
  `send_ok` int NOT NULL COMMENT '0失败 1成功',
  `send_msg` varchar(255) DEFAULT NULL COMMENT '发送结果说明',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_offer_remind_log_offer_id` (`offer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_password_reset
CREATE TABLE `t_hr_password_reset` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `token` varchar(128) NOT NULL COMMENT '重置令牌/验证码',
  `channel` varchar(16) NOT NULL COMMENT '发送渠道: email/phone',
  `target` varchar(64) NOT NULL COMMENT '目标: 脱敏邮箱/手机号',
  `status` varchar(16) NOT NULL COMMENT 'pending/used/expired',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `ix_t_hr_password_reset_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_recruit_channel
CREATE TABLE `t_hr_recruit_channel` (
  `channel_name` varchar(50) NOT NULL COMMENT '渠道名称',
  `channel_type` int NOT NULL COMMENT '1官网 2第三方 3内推',
  `status` int NOT NULL COMMENT '1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_recruit_demand
CREATE TABLE `t_hr_recruit_demand` (
  `demand_no` varchar(32) NOT NULL COMMENT '业务单号 DM2026070001',
  `dept_id` bigint NOT NULL COMMENT '发起部门ID',
  `position_id` bigint NOT NULL COMMENT '招聘岗位ID',
  `recruit_type` int NOT NULL COMMENT '1社招 2校招 3实习 4内推',
  `plan_headcount` int NOT NULL COMMENT '计划招聘人数',
  `filled_count` int NOT NULL COMMENT '已入职人数',
  `expect_entry_date` date DEFAULT NULL COMMENT '期望到岗日期',
  `jd_content` text COMMENT 'JD全文',
  `edu_min` varchar(64) DEFAULT NULL COMMENT '最低学历要求',
  `exp_min` int DEFAULT NULL COMMENT '最低工作年限',
  `work_city` varchar(64) DEFAULT NULL COMMENT '工作城市',
  `publishing_channels` json DEFAULT NULL COMMENT '发布渠道ID数组',
  `demand_status` int NOT NULL COMMENT '0草稿 1审批中 2通过 3驳回 4完结 5取消',
  `cancel_at` datetime DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` varchar(512) DEFAULT NULL COMMENT '取消原因',
  `audit_flow` json DEFAULT NULL COMMENT '审批节点快照',
  `headcount_reserve_json` json DEFAULT NULL COMMENT 'HC占用快照',
  `creator_id` bigint NOT NULL COMMENT '发起人用户ID',
  `hr_owner_id` bigint DEFAULT NULL COMMENT '跟进HR用户ID',
  `internal_searched` int NOT NULL COMMENT '是否已检索内部员工',
  `resume_searched` int NOT NULL COMMENT '是否已检索外部简历库',
  `approved_at` datetime DEFAULT NULL COMMENT '审批通过时间',
  `closed_at` datetime DEFAULT NULL COMMENT '需求关闭时间',
  `is_internal_given_up` int NOT NULL COMMENT '是否放弃内部人才',
  `recommend_limit` int DEFAULT NULL COMMENT '推荐人数上限 NULL=用全局',
  `salary_range` varchar(64) DEFAULT NULL COMMENT '薪资范围',
  `urgency` varchar(16) NOT NULL COMMENT '紧急度 very/high/normal',
  `required_skills` json DEFAULT NULL COMMENT '必备技能列表',
  `plus_skills` json DEFAULT NULL COMMENT '加分技能列表',
  `position_name` varchar(128) DEFAULT NULL COMMENT '岗位名称（前端文本直存）',
  `dept_name` varchar(64) DEFAULT NULL COMMENT '部门名称（前端文本直存）',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `demand_no` (`demand_no`),
  KEY `idx_t_hr_recruit_demand_dept_id` (`dept_id`),
  KEY `idx_t_hr_recruit_demand_position_id` (`position_id`)
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_recruit_mail_account
CREATE TABLE `t_hr_recruit_mail_account` (
  `account_name` varchar(64) NOT NULL COMMENT '账号别名',
  `email_address` varchar(128) NOT NULL COMMENT '收简历邮箱地址',
  `imap_host` varchar(128) DEFAULT NULL COMMENT 'IMAP服务器',
  `imap_port` int DEFAULT NULL COMMENT '端口',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人HR',
  `status` int NOT NULL COMMENT '1启用 0停用',
  `monitor_folder` varchar(128) DEFAULT NULL COMMENT '监控文件夹 NULL=默认INBOX',
  `mail_type` varchar(32) DEFAULT NULL COMMENT '邮箱类型: qq/163/gmail/corp/custom',
  `sync_freq` int NOT NULL COMMENT '同步周期(分钟)',
  `password_encrypted` varchar(256) DEFAULT NULL COMMENT '加密存储的密码/授权码',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最近同步时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `email_address` (`email_address`),
  KEY `idx_t_hr_recruit_mail_account_owner_user_id` (`owner_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_recruit_process
CREATE TABLE `t_hr_recruit_process` (
  `process_no` varchar(32) NOT NULL COMMENT '流程编号',
  `demand_id` bigint NOT NULL COMMENT '所属需求ID',
  `resume_id` bigint NOT NULL COMMENT '外部简历ID',
  `candidate_id` bigint NOT NULL COMMENT '候选人ID',
  `process_status` int NOT NULL COMMENT '0待筛 1邀约 2一面 3二面 4淘汰 5待Offer 6接受 7放弃 8入职',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `process_no` (`process_no`),
  KEY `idx_t_hr_recruit_process_demand_id` (`demand_id`),
  KEY `idx_t_hr_recruit_process_resume_id` (`resume_id`),
  KEY `idx_t_hr_recruit_process_candidate_id` (`candidate_id`)
) ENGINE=InnoDB AUTO_INCREMENT=210 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_resume
CREATE TABLE `t_hr_resume` (
  `candidate_id` bigint NOT NULL COMMENT '关联候选人ID',
  `resume_file_id` bigint DEFAULT NULL COMMENT '简历附件ID files.id',
  `storage_time` datetime NOT NULL COMMENT '简历入库时间',
  `base_score` decimal(4,1) NOT NULL COMMENT '时效分',
  `work_exp_text` text COMMENT '工作经历摘要',
  `extract_json` json DEFAULT NULL COMMENT 'AI解析完整结构化数据',
  `touch_json` json DEFAULT NULL COMMENT '触达/邀约/储备记录',
  `source_channel_id` bigint DEFAULT NULL COMMENT '来源渠道ID',
  `mail_account_id` bigint DEFAULT NULL COMMENT '采集邮箱ID',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_resume_candidate_id` (`candidate_id`),
  KEY `idx_t_hr_resume_resume_file_id` (`resume_file_id`),
  KEY `idx_t_hr_resume_source_channel_id` (`source_channel_id`),
  KEY `idx_t_hr_resume_mail_account_id` (`mail_account_id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_resume_match
CREATE TABLE `t_hr_resume_match` (
  `resume_id` bigint NOT NULL COMMENT '外部简历ID',
  `demand_id` bigint NOT NULL COMMENT '需求ID',
  `match_score` decimal(4,1) NOT NULL COMMENT '岗位匹配核心分',
  `score_detail` json DEFAULT NULL COMMENT '各维度打分明细',
  `calculate_time` datetime NOT NULL COMMENT '打分时间',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_t_hr_resume_match_resume_id` (`resume_id`),
  KEY `idx_t_hr_resume_match_demand_id` (`demand_id`)
) ENGINE=InnoDB AUTO_INCREMENT=159 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_role_menu_permission
CREATE TABLE `t_hr_role_menu_permission` (
  `role_code` varchar(32) NOT NULL COMMENT '角色编码',
  `menu_id` varchar(64) NOT NULL COMMENT '菜单标识: recruit-dashboard/recruit-demand等',
  `enabled` int NOT NULL COMMENT '1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  KEY `ix_t_hr_role_menu_permission_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_score_rule
CREATE TABLE `t_hr_score_rule` (
  `score_scene` int NOT NULL COMMENT '1时效分规则 2岗位匹配规则',
  `weight_json` json NOT NULL COMMENT '时效衰减+两套分数线',
  `pool_min_score` decimal(4,1) DEFAULT NULL COMMENT '存量简历准入最低分',
  `auto_invite_min_score` decimal(4,1) DEFAULT NULL COMMENT '自动邀约分数线',
  `status` int NOT NULL COMMENT '1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_search_log
CREATE TABLE `t_hr_search_log` (
  `demand_id` bigint NOT NULL COMMENT '关联需求ID',
  `search_type` int NOT NULL COMMENT '1内部员工库 2外部简历库',
  `search_at` datetime NOT NULL COMMENT '检索执行时间',
  `match_total` int NOT NULL COMMENT '合格匹配人数',
  `remark` varchar(512) DEFAULT NULL COMMENT '筛选条件备注',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;

-- Table: t_hr_tag_dict
CREATE TABLE `t_hr_tag_dict` (
  `tag_code` varchar(64) NOT NULL COMMENT '标签唯一编码',
  `tag_name` varchar(64) NOT NULL COMMENT '前端展示标签名',
  `tag_category` varchar(32) NOT NULL COMMENT '一级分类 edu/school/skill/industry/cert/exp',
  `tag_sub_category` varchar(32) DEFAULT NULL COMMENT '二级细分',
  `match_weight` decimal(5,2) NOT NULL COMMENT 'JD匹配权重',
  `support_target` int NOT NULL COMMENT '1仅简历 2仅员工 3通用',
  `sort_num` int NOT NULL COMMENT '排序号',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注/解析规则',
  `status` int NOT NULL COMMENT '1启用 0停用',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `is_deleted` int NOT NULL COMMENT '逻辑删除: 0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tag_code` (`tag_code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3;
