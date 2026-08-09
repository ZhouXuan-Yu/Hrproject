# 数据库迁移指南

## 一、总原则

**数据库表结构零变更**。Java 后端直接复用现有 MySQL 数据库，JPA 实体严格映射现有表。

## 二、迁移策略

1. **首次部署**：Flyway `baseline-on-migrate=true`，将现有 schema 作为 V1 baseline
2. **后续变更**：所有 DDL 走 Flyway 增量脚本 `V2__xxx.sql`
3. **禁止**：手工改表、JPA ddl-auto 生成（配置为 `ddl-auto: none`）

## 三、现有表 → JPA 实体映射

### IAM 底座（只读引用）
| 表 | JPA 实体 | 模块 |
|----|---------|------|
| t_core_user | IamUser | hr-auth |
| t_core_dept | IamDept | hr-auth |
| t_core_position | IamPosition | hr-auth |

### 招聘核心
| 表 | JPA 实体 | 模块 |
|----|---------|------|
| t_hr_recruit_demand | RecruitDemand | hr-demand |
| t_hr_demand_approval | DemandApproval | hr-demand |
| t_hr_dept_hc | DeptHC | hr-demand |
| t_hr_candidate | Candidate | hr-talent |
| t_hr_resume | Resume | hr-talent |
| t_hr_candidate_tag_rel | CandidateTagRel | hr-talent |
| t_hr_employee | Employee | hr-talent |
| t_hr_employee_tag_rel | EmployeeTagRel | hr-talent |
| t_hr_internal_match_log | InternalMatchLog | hr-talent |
| t_hr_recruit_process | RecruitProcess | hr-interview |
| t_hr_resume_match | ResumeMatch | hr-interview |
| t_hr_search_log | SearchLog | hr-interview |
| t_hr_interview_slot | InterviewSlot | hr-interview |
| t_hr_interview_book | InterviewBook | hr-interview |
| t_hr_interview_record | InterviewRecord | hr-interview |
| t_hr_hire_event | HireEvent | hr-hire |
| t_hr_offer | Offer | hr-hire |
| t_hr_offer_remind_log | OfferRemindLog | hr-hire |
| t_hr_entry | Entry | hr-hire |

### 基础设施 / 辅助
| 表 | JPA 实体 | 模块 |
|----|---------|------|
| files | File | hr-common |
| t_hr_tag_dict | TagDict | hr-config |
| t_hr_recruit_channel | RecruitChannel | hr-config |
| t_hr_score_rule | ScoreRule | hr-config |
| t_hr_recruit_mail_account | RecruitMailAccount | hr-config |
| t_hr_chat_log | ChatLog | hr-config |
| t_hr_notify_template | NotifyTemplate | hr-config |
| t_hr_audit_log | AuditLog | hr-config |
| t_hr_api_key | ApiKeyConfig | hr-config |
| t_hr_ai_knowledge_base | AiKnowledgeBase | hr-config |
| t_hr_role_menu_permission | RoleMenuPermission | hr-auth |
| t_hr_password_reset | PasswordResetToken | hr-auth |
| t_hr_approval_identity | RecruitApprovalIdentity | hr-demand |
| t_hr_mail_log | MailLog | hr-integration |

## 四、Flyway 脚本规范

```
hr-bootstrap/src/main/resources/db/migration/
├── V1__baseline.sql    # 首次部署：现有 schema 完整 DDL
├── V2__xxx.sql         # 后续变更
└── ...
```

命名规则：`V{版本}__{描述}.sql`，如 `V2__add_index_on_demand_status.sql`

## 五、软删除约定

所有业务表有 `is_deleted` 字段（0 未删，1 已删）。
- JPA 查询默认加 `AND is_deleted = 0`
- 逻辑删除用 `update set is_deleted = 1`
- 物理删除仅用于候选人 PIPL 权利（hard-delete）

## 六、时间字段约定

- `created_at` / `updated_at`：自动填充
- `create_time` / `update_time`：由 JPA AuditingEntityListener 管理
- 时区统一 `Asia/Shanghai`

## 七、常见风险

| 风险 | 应对 |
|------|------|
| Flyway 与现有表校验失败 | 设置 `baseline-on-migrate=true`，`baseline-version=1` |
| 表名大小写 | MySQL 表名小写（Linux），JPA `@Table(name="...")` 精确指定 |
| JSON 字段 | 使用 `@JdbcTypeCode(SqlTypes.JSON)` 映射 MySQL JSON 列 |
