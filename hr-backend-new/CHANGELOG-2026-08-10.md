# Changelog — 2026-08-10

Flask → Java Spring Boot 迁移记录。

---

## 总览

| 批次 | 内容 | 新建文件 | 修改文件 | 状态 |
|------|------|---------|---------|------|
| Phase 1 | Dedup 模块 | 5 | 4 | ✅ |
| Phase 2.1 | Auth 模块缺口 | 0 | 8 | ✅ |
| Phase 2.2 | Config 模块缺口 | 0 | 1 | ✅ |
| Phase 2.3 | Hire 模块 followup | 2 | 2 | ✅ |
| Phase 2.4 | Dashboard 首页 | 0 | 2 | ✅ |
| Phase 3 | ConfirmController H5 | 0 | 1 | ✅ |
| Phase 4 | 缺失 Entity | 4 | 0 | ✅ |
| **合计** | | **11** | **18** | |

---

## Phase 1: Dedup 模块

### 新建文件
| 文件 | 说明 |
|------|------|
| `hr-dedup/pom.xml` | 新模块 POM，依赖 hr-common + hr-talent |
| `hr-dedup/.../DedupController.java` | 3 端点：POST /check, GET /scan, POST /merge |
| `hr-dedup/.../DedupService.java` | SHA-256 手机哈希 + 3层匹配 + 6步合并 |
| `hr-talent/.../entity/CandidateTagRel.java` | 候选人标签关联 Entity |
| `hr-talent/.../repository/CandidateTagRelRepository.java` | 标签关联 Repository |

### 修改文件
| 文件 | 修改 |
|------|------|
| `pom.xml` | + `<module>hr-dedup</module>`, + dependencyManagement |
| `hr-bootstrap/pom.xml` | + hr-dedup 依赖 |
| `hr-talent/.../CandidateRepository.java` | +5 查询方法 (mobileHash/email/name + 分组查询) |
| `hr-talent/.../RecruitProcessRepository.java` | + findByCandidateId |

### 回退
```bash
cd hr-backend-new
git checkout pom.xml hr-bootstrap/pom.xml
git checkout hr-talent/src/main/java/com/hr/talent/repository/CandidateRepository.java
git checkout hr-talent/src/main/java/com/hr/talent/repository/RecruitProcessRepository.java
rm -rf hr-dedup/
rm -f hr-talent/src/main/java/com/hr/talent/entity/CandidateTagRel.java
rm -f hr-talent/src/main/java/com/hr/talent/repository/CandidateTagRelRepository.java
```

---

## Phase 2.1: Auth 模块

### pendingAccounts 修复（关键 Bug）
- **问题**: 旧代码查 `mustChangePassword==1` 的 IamUser — 语义完全错误
- **修复**: 原生 SQL JOIN Candidate→Resume→RecruitProcess→Demand，查 hired + process_status IN (6,8) + 无 IamUser 账号的候选人
- **文件**:
  - `hr-auth/.../service/UserManagementService.java` — 新加 `findPendingAccounts()`
  - `hr-auth/.../controller/UserManagementController.java` — 改为委托 service

### register 端点（新增）
- `POST /api/auth/register`: 自助注册，验证码存 PasswordResetToken，首个用户自动 admin
- **文件**:
  - `hr-auth/.../controller/AuthController.java` — 新加 register 端点
  - `hr-auth/.../service/AuthService.java` — 新加 `register()` 方法
  - `hr-auth/.../repository/PasswordResetTokenRepository.java` — 新加查询方法

### sendResetMail 修复
- **问题**: 旧代码始终返回 false，导致所有密码重置邮件静默失败
- **修复**: 可选注入 JavaMailSender，配置后真实发送邮件，未配置时返回 false
- **文件**:
  - `hr-auth/pom.xml` — 加 `spring-boot-starter-mail`
  - `hr-auth/.../service/AuthService.java` — `sendResetMail()` 改为真实实现

### interviewers 修复
- `hr-auth/.../controller/OrgController.java` — 加 temp_interviewer/admin 角色 + @RequireRole
- `hr-auth/.../repository/IamUserRepository.java` — 加 `findByRoleCodeInAndStatusAndIsDeleted`

### 回退
```bash
cd hr-backend-new
git checkout hr-auth/pom.xml
git checkout hr-auth/src/main/java/com/hr/auth/controller/AuthController.java
git checkout hr-auth/src/main/java/com/hr/auth/controller/OrgController.java
git checkout hr-auth/src/main/java/com/hr/auth/controller/UserManagementController.java
git checkout hr-auth/src/main/java/com/hr/auth/service/AuthService.java
git checkout hr-auth/src/main/java/com/hr/auth/service/UserManagementService.java
git checkout hr-auth/src/main/java/com/hr/auth/repository/IamUserRepository.java
git checkout hr-auth/src/main/java/com/hr/auth/repository/PasswordResetTokenRepository.java
```

---

## Phase 2.2: Config 模块

### 新增端点
| 端点 | 说明 |
|------|------|
| `POST /api/config/email-accounts/detect` | IMAP 自动探测（4层回退） |
| `POST /api/config/email-accounts/get-preview` | 邮箱预览（不连接） |
| `GET /api/config/tencent-meeting/status` | 腾讯会议配置状态 |
| `GET /api/config/feishu/status` | 飞书配置状态 |

### 修复
- `syncAllEmailAccounts()` — 去掉"IMAP 采集由 AI 服务负责，当前跳过"静默失败，改为返回有意义的状态

### 修改文件
| 文件 | 修改 |
|------|------|
| `hr-config/.../controller/ConfigController.java` | +4 端点 |
| `hr-config/.../service/ConfigService.java` | +6 方法 (detect/preview/status/sync修复) |

### 回退
```bash
git checkout hr-config/src/main/java/com/hr/config/controller/ConfigController.java
git checkout hr-config/src/main/java/com/hr/config/service/ConfigService.java
```

---

## Phase 2.3: Hire 模块

### 新增
| 项目 | 说明 |
|------|------|
| `POST /api/hire/offers/followup` | 倒计时提醒 + 超时淘汰 |
| `OfferRemindLog` Entity | 提醒去重（24h 内不重复发送） |

### 新建文件
| 文件 | 说明 |
|------|------|
| `hr-hire/.../entity/OfferRemindLog.java` | Offer 提醒日志 Entity |
| `hr-hire/.../repository/OfferRemindLogRepository.java` | 提醒日志 Repository |

### 修改文件
| 文件 | 修改 |
|------|------|
| `hr-hire/.../controller/HireController.java` | + followup 端点 |
| `hr-hire/.../service/HireService.java` | + `offerFollowup()` 方法 + OfferRemindLogRepository 注入 |

### 回退
```bash
git checkout hr-hire/src/main/java/com/hr/hire/controller/HireController.java
git checkout hr-hire/src/main/java/com/hr/hire/service/HireService.java
rm -f hr-hire/src/main/java/com/hr/hire/entity/OfferRemindLog.java
rm -f hr-hire/src/main/java/com/hr/hire/repository/OfferRemindLogRepository.java
```

---

## Phase 2.4: Dashboard 首页

### 新增
| 端点 | 说明 |
|------|------|
| `GET /api/dashboard/home` | 角色感知工作台（admin/hr 看全局，dept_head 看部门需求，interviewer 看面试安排） |

### 修改文件
| 文件 | 修改 |
|------|------|
| `hr-dashboard/.../controller/DashboardController.java` | + home 端点 |
| `hr-dashboard/.../service/DashboardService.java` | + `getHomeData()` + `getMyDemands()` + `getMyInterviews()` |

### 回退
```bash
git checkout hr-dashboard/src/main/java/com/hr/dashboard/controller/DashboardController.java
git checkout hr-dashboard/src/main/java/com/hr/dashboard/service/DashboardService.java
```

---

## Phase 3: ConfirmController H5 渲染

### 修改
- **改造前**: `GET /confirm/{token}` 仅返回 `{kind, ref, expired}` JSON
- **改造后**: 加载完整数据（候选人/岗位/时间/地点/Offer内容）→ 渲染移动端 H5 HTML 页面
- 对齐 Flask `_PAGE` 模板：内联 CSS + vanilla JS，interview/offer 两种页面
- 已确认/已拒绝状态回显（idempotent 检测）

### 修改文件
| 文件 | 修改 |
|------|------|
| `hr-bootstrap/.../ConfirmController.java` | 完全重写 confirmPage/loadPageData/renderPage/renderError |

### 回退
```bash
git checkout hr-bootstrap/src/main/java/com/hr/bootstrap/controller/ConfirmController.java
```

---

## Phase 4: 缺失 Entity

### 新建文件
| 文件 | 表 | 说明 |
|------|-----|------|
| `hr-talent/.../CandidateTagRel.java` | t_hr_candidate_tag_rel | 候选人标签关联 (Phase 1 创建) |
| `hr-hire/.../OfferRemindLog.java` | t_hr_offer_remind_log | Offer 提醒日志 (Phase 2.3 创建) |
| `hr-demand/.../DeptHC.java` | t_hr_dept_hc | 部门编制 |
| `hr-demand/.../RecruitApprovalIdentity.java` | t_hr_approval_identity | 审批身份 |
| `hr-config/.../TagDict.java` | t_hr_tag_dict | 标签字典 |

### 仍缺失（低优先级 — 日志/分析表）
| Entity | 表 | 注 |
|--------|-----|-----|
| EmployeeTagRel | t_hr_employee_tag_rel | 内部员工标签 |
| InternalMatchLog | t_hr_internal_match_log | 内部匹配日志 |
| SearchLog | t_hr_search_log | 搜索日志 |
| ChatLog | t_hr_chat_log | AI 对话日志 |

### 回退
```bash
rm -f hr-demand/src/main/java/com/hr/demand/entity/DeptHC.java
rm -f hr-demand/src/main/java/com/hr/demand/entity/RecruitApprovalIdentity.java
rm -f hr-config/src/main/java/com/hr/config/entity/TagDict.java
```

---

## 验证

编译验证（需要 Maven 环境）：
```bash
cd hr-backend-new
mvn compile -pl hr-bootstrap -am
```

API 对比验证（需要启动服务）：
```bash
# Flask 端
curl -s http://127.0.0.1:5000/api/dedup/check -X POST -H 'Content-Type: application/json' -d '{"phone":"13800138000"}'
# Java 端
curl -s http://127.0.0.1:8080/api/dedup/check -X POST -H 'Content-Type: application/json' -d '{"phone":"13800138000"}'
```
