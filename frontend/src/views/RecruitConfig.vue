<template>
  <WorkbenchLayout title="招聘基础配置" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <span class="admin-only">仅管理员可见</span>
    </template>

    <StatCardRow :cards="statCards" />
    <section class="hero-page-summary" style="display:none" aria-hidden="true"></section>

    <div class="permission-bar">
      本页面仅<b>系统管理员</b>可操作 · HR 无配置权限 · 配置变更即时生效，请谨慎操作
    </div>

    <!-- 邮箱配置 -->
    <BaseAccordion title="邮箱配置" :open="true">
      <div class="accordion-desc">
        配置收简历邮箱，系统按规则定时拉取并解析入库。后台负责同步与记录，HR 可在人才库复核结果。<br>
        解析完成的简历统一沉淀至<b>「人才库 - 简历储备库」</b>，标记来源渠道「邮箱采集」。
      </div>
      <table class="config-table">
        <thead><tr><th>邮箱地址</th><th>类型</th><th>同步周期</th><th>连接状态</th><th>最近同步</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(acct, i) in emailAccounts" :key="acct.id || i">
            <td>{{ acct.address }}</td>
            <td>{{ emailTypeLabel(acct.type) }}</td>
            <td>{{ acct.freq }}</td>
            <td><span :style="{ color: acct.statusColor === 'done' ? 'var(--c-done)' : 'var(--c-warn)', fontWeight: 600 }">{{ acct.status }}</span></td>
            <td>{{ acct.lastSync }}</td>
            <td class="row-actions">
              <a href="#" class="btn btn-text btn-sm" @click.prevent="testEmailConn(acct)">{{ acct.status === '异常' ? '重新连接' : '测试连接' }}</a>
              <a href="#" class="btn btn-text btn-sm" @click.prevent="editEmail(acct)">编辑</a>
              <a href="#" class="btn btn-text-danger btn-sm" @click.prevent="deleteEmail(acct)">删除</a>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-primary btn-sm" @click="openAddEmail">+ 添加邮箱账号</button>
      <hr class="config-divider">
      <div class="form-row">
        <div class="form-group"><label>简历识别规则</label><select><option>解析服务识别（推荐）</option><option>关键词匹配</option><option>发件人域名</option></select></div>
        <div class="form-group"><label>垃圾简历过滤</label><select><option>标准过滤（默认）</option><option>宽松</option><option>严格</option></select></div>
      </div>
      <div class="form-group"><label>附件格式白名单</label><input type="text" value="PDF, DOCX, DOC" readonly style="background:#fafbfc"></div>
    </BaseAccordion>

    <!-- 渠道配置 -->
    <BaseAccordion title="渠道配置">
      <table class="config-table">
        <thead><tr><th>渠道编码</th><th>渠道名称</th><th>类型</th><th>月均费用</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(ch, i) in channels" :key="ch.id || i">
            <td>{{ ch.code }}</td>
            <td>{{ ch.name }}</td>
            <td>{{ ch.type }}</td>
            <td><input type="text" :value="ch.cost" style="width:80px;padding:4px 6px;border:1px solid var(--c-border);border-radius:4px;font-size:12px" @blur="updateChanCost(ch, $event)" @keydown.enter="updateChanCost(ch, $event)" @keydown.escape="$event.target.blur()"></td>
            <td>
              <StatusBadge :type="ch.status === '启用' ? 'done' : 'warn'">{{ ch.status }}</StatusBadge>
            </td>
            <td class="row-actions">
              <a href="#" class="btn btn-text btn-sm" @click.prevent="toggleChanStatus(ch)">{{ ch.status === '启用' ? '停用' : '启用' }}</a>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-primary btn-sm" @click="showChanModal = true">+ 新增渠道</button>
    </BaseAccordion>

    <!-- 打分规则 -->
    <BaseAccordion title="打分规则配置" ref="scoreRef">
      <div class="accordion-desc">综合推荐分 = 画像分 × 权重 + 匹配分 × 权重。直接投递无衰减，存量简历叠加时间衰减系数。</div>
      <div class="form-row">
        <div class="form-group"><label>画像分权重 <span class="field-hint">候选人硬底子占比</span></label><input type="number" v-model.number="scoreRules.profileWeight" step="0.05" style="width:100px"></div>
        <div class="form-group"><label>匹配分权重 <span class="field-hint">岗位适配度占比</span></label><input type="number" v-model.number="scoreRules.matchWeight" step="0.05" style="width:100px"></div>
      </div>
      <div class="form-row">
        <div class="form-group"><label>衰减系数 ≤30天</label><input type="number" v-model.number="scoreRules.decay30" step="0.05" style="width:100px"></div>
        <div class="form-group"><label>衰减系数 30~90天</label><input type="number" v-model.number="scoreRules.decay90" step="0.05" style="width:100px"></div>
      </div>
      <div class="form-row">
        <div class="form-group"><label>衰减系数 >90天</label><input type="number" v-model.number="scoreRules.decayOver90" step="0.05" style="width:100px"></div>
        <div class="form-group"><label>匹配及格线 <span class="field-hint">低于此分不过滤但不推荐</span></label><input type="number" v-model.number="scoreRules.passLine" style="width:100px"></div>
      </div>
      <div class="form-row">
        <div class="form-group"><label>推荐人数上限</label><input type="number" v-model.number="scoreRules.topCount" style="width:100px"></div>
        <div class="form-group"><label>检索时间范围</label><select v-model="scoreRules.searchRange" style="width:100%;padding:8px 12px;border:1px solid var(--c-border);border-radius:6px"><option>近 3 个月</option><option>近 6 个月</option><option>全部</option></select></div>
      </div>
      <button class="btn btn-primary btn-sm" @click="saveRules" :disabled="ruleSaving">{{ ruleSaving ? '保存中...' : '保存规则' }}</button>
      <span v-if="ruleMsg" class="save-msg" :class="ruleMsgType">{{ ruleMsg }}</span>
    </BaseAccordion>

    <!-- 通知模板 -->
    <BaseAccordion title="通知模板">
      <table class="config-table">
        <thead><tr><th>模板名称</th><th>类型</th><th>发送方式</th><th>最近更新</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(tpl, i) in notifyTemplates" :key="tpl.id || i">
            <td>{{ tpl.name }}</td>
            <td>{{ tpl.type }}</td>
            <td><input type="text" :value="tpl.method" style="width:100px;padding:4px 6px;border:1px solid var(--c-border);border-radius:4px;font-size:12px" @blur="updateTplMethod(tpl, $event)" @keydown.enter="updateTplMethod(tpl, $event)" @keydown.escape="$event.target.blur()"></td>
            <td>{{ tpl.updated }}</td>
            <td class="row-actions">
              <a href="#" class="btn btn-text btn-sm" @click.prevent="editTemplate(tpl)">编辑</a>
              <a href="#" class="btn btn-text btn-sm" @click.prevent="previewTemplate(tpl)">预览</a>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-primary btn-sm" style="margin-top:10px" @click="openAddTemplate">+ 新增模板</button>
    </BaseAccordion>

    <!-- AI 知识库 -->
    <BaseAccordion title="AI 知识库" data-testid="knowledge-base-panel">
      <div class="accordion-desc">
        维护公司基础信息、招聘口径和 AI 补充上下文。JD 生成、候选人沟通话术等 DeepSeek 分析会读取这里的内容。
      </div>
      <div class="form-row">
        <div class="form-group"><label>公司名称</label><input data-testid="kb-company-name" type="text" v-model="knowledgeForm.companyName" placeholder="XX公司"></div>
        <div class="form-group"><label>行业/业务</label><input data-testid="kb-industry" type="text" v-model="knowledgeForm.industry" placeholder="企业服务 / 智能招聘"></div>
      </div>
      <div class="form-group"><label>官网</label><input data-testid="kb-website" type="text" v-model="knowledgeForm.website" placeholder="https://example.com"></div>
      <div class="form-group"><label>公司介绍</label><textarea data-testid="kb-company-profile" v-model="knowledgeForm.companyProfile" rows="4" class="kb-textarea" placeholder="写清楚公司做什么、服务谁、当前业务阶段和组织特点"></textarea></div>
      <div class="form-group"><label>招聘口径</label><textarea data-testid="kb-hiring-principles" v-model="knowledgeForm.hiringPrinciples" rows="4" class="kb-textarea" placeholder="写清楚岗位分析、候选人沟通、合规表达的基础要求"></textarea></div>
      <div class="form-group"><label>AI 补充上下文</label><textarea data-testid="kb-ai-context" v-model="knowledgeForm.aiContext" rows="4" class="kb-textarea" placeholder="补充前面 AI 功能需要长期遵守的上下文"></textarea></div>
      <button data-testid="kb-save" class="btn btn-primary btn-sm" :disabled="knowledgeSaving" @click="saveKnowledgeBase">
        {{ knowledgeSaving ? '保存中...' : '保存知识库' }}
      </button>
    </BaseAccordion>

    <!-- API Key 管理 -->
    <BaseAccordion title="API 密钥管理">
      <div class="accordion-desc">
        配置外部服务的 API 密钥，所有密钥 <b>AES-256-GCM 加密存储</b>，不可逆向查看。输入新值即覆盖。
      </div>
      <div v-for="keyInfo in secretKeys" :key="keyInfo.key_name" class="secret-key-row">
        <div class="secret-key-info">
          <div class="secret-key-label">{{ keyInfo.label }}</div>
          <div class="secret-key-desc">{{ keyInfo.desc }}</div>
          <a
            v-if="keyInfo.link"
            class="secret-key-link"
            :href="keyInfo.link"
            target="_blank"
            rel="noopener noreferrer"
          >{{ keyInfo.link_text || '去获取密钥' }}
            <svg viewBox="0 0 24 24" style="width:12px;height:12px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
          </a>
        </div>
        <div class="secret-key-input-group">
          <input
            :type="keyInfo.showInput ? 'text' : 'password'"
            :id="'key-' + keyInfo.key_name"
            :placeholder="keyInfo.has_value ? keyInfo.masked : '输入密钥...'"
            v-model="keyInfo.inputValue"
            class="secret-key-field"
            autocomplete="off"
          >
          <button
            class="btn btn-text btn-sm secret-key-toggle"
            @click="keyInfo.showInput = !keyInfo.showInput"
            :aria-label="keyInfo.showInput ? '隐藏输入' : '显示输入'"
          >{{ keyInfo.showInput ? '隐藏' : '显示' }}</button>
          <button
            class="btn btn-text btn-sm"
            :disabled="!keyInfo.has_value || keyInfo.testing"
            @click="runApiKeyTest(keyInfo)"
            :aria-label="'测试 ' + keyInfo.label + ' 连接'"
          >{{ keyInfo.testing ? '测试中...' : '测试连接' }}</button>
          <button
            class="btn btn-primary btn-sm"
            :disabled="!keyInfo.inputValue || keyInfo.inputValue === keyInfo.masked"
            @click="saveApiKey(keyInfo)"
            :aria-label="'保存 ' + keyInfo.label"
          >{{ keyInfo.saving ? '保存中...' : '保存' }}</button>
        </div>
        <div
          v-if="keyInfo.testStatus"
          class="secret-key-test-result"
          :class="keyInfo.testStatus"
          role="status"
        >
          <svg v-if="keyInfo.testStatus === 'ok'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
          <svg v-else-if="keyInfo.testStatus === 'fail'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <span>{{ keyInfo.testMessage }}</span>
          <span v-if="keyInfo.testSource" class="secret-key-test-source">（密钥来源：{{ keyInfo.testSource === 'env' ? '环境变量' : '数据库配置' }}）</span>
        </div>
      </div>
      <div class="secret-key-note">
        <svg viewBox="0 0 24 24" style="width:14px;height:14px;stroke:var(--c-warn);fill:none;stroke-width:2;stroke-linecap:round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        密钥保存后即加密存储，仅可覆盖不可查看。环境变量中的密钥优先于此处配置。
      </div>
    </BaseAccordion>

    <!-- 飞书配置 -->
    <BaseAccordion title="飞书配置">
      <div class="accordion-desc">
        配置飞书开放平台应用凭证（App ID 与 App Secret 需配对填写），用于面试通知、审批推送。App Secret <b>AES-256-GCM 加密存储</b>，不可逆向查看。留空的字段保持不变。
        <a class="secret-key-link" href="https://open.feishu.cn/app" target="_blank" rel="noopener noreferrer">没有应用？去飞书开放平台创建应用，获取 App ID 与 App Secret
          <svg viewBox="0 0 24 24" style="width:12px;height:12px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
        </a>
        <br>
        当前状态：<span class="tencent-status" :class="feishuStatus.configured ? 'ok' : 'off'">{{ feishuStatus.configured ? '已配置' : '未配置' }}</span>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>App ID</label>
          <input type="text" v-model="feishuForm.appId" :placeholder="feishuKeys.feishu_app_id?.has_value ? feishuKeys.feishu_app_id.masked : '输入 App ID（cli_ 开头）...'" class="secret-key-field" autocomplete="off">
        </div>
        <div class="form-group">
          <label>App Secret <span class="field-hint">加密存储，留空保持不变</span></label>
          <div class="secret-key-input-group">
            <input :type="feishuShowSecret ? 'text' : 'password'" v-model="feishuForm.appSecret" :placeholder="feishuKeys.feishu?.has_value ? feishuKeys.feishu.masked : '输入 App Secret...'" class="secret-key-field" autocomplete="off">
            <button class="btn btn-text btn-sm secret-key-toggle" @click="feishuShowSecret = !feishuShowSecret" :aria-label="feishuShowSecret ? '隐藏输入' : '显示输入'">{{ feishuShowSecret ? '隐藏' : '显示' }}</button>
          </div>
        </div>
      </div>
      <button class="btn btn-primary btn-sm" @click="saveFeishu" :disabled="feishuSaving">{{ feishuSaving ? '保存中...' : '保存' }}</button>
      <button class="btn btn-text btn-sm" @click="testFeishu" :disabled="feishuTesting || !feishuStatus.configured">{{ feishuTesting ? '测试中...' : '测试连接' }}</button>
      <span v-if="feishuMsg" class="save-msg" :class="feishuMsgType">{{ feishuMsg }}</span>
      <div v-if="feishuTest.status" class="secret-key-test-result" :class="feishuTest.status" role="status">
        <svg v-if="feishuTest.status === 'ok'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
        <svg v-else-if="feishuTest.status === 'fail'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        <span>{{ feishuTest.message }}</span>
        <span v-if="feishuTest.source" class="secret-key-test-source">（密钥来源：{{ feishuTest.source === 'env' ? '环境变量' : '数据库配置' }}）</span>
      </div>
    </BaseAccordion>

    <!-- 腾讯会议配置 -->
    <BaseAccordion title="腾讯会议配置">
      <div class="accordion-desc">
        配置腾讯会议开放平台凭证，用于创建「腾讯会议」类型的线上面试。SecretKey <b>AES-256-GCM 加密存储</b>，不可逆向查看。留空的字段保持不变。
        <a class="secret-key-link" href="https://meeting.tencent.com/support-doc-detail/776/index.html" target="_blank" rel="noopener noreferrer">没有凭证？去腾讯会议官网「用户中心 → 高级 → RestAPI」创建密钥对（需企业版/商业版）
          <svg viewBox="0 0 24 24" style="width:12px;height:12px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
        </a>
        <br>
        当前状态：<span class="tencent-status" :class="tencentStatus.configured ? 'ok' : 'off'">{{ tencentStatus.configured ? '已配置' : '未配置' }}</span>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>AppId</label>
          <input type="text" v-model="tencentForm.appid" :placeholder="tencentKeys.tencent_appid?.has_value ? tencentKeys.tencent_appid.masked : '输入 AppId...'" class="secret-key-field" autocomplete="off">
        </div>
        <div class="form-group">
          <label>SecretId</label>
          <input type="text" v-model="tencentForm.secretid" :placeholder="tencentKeys.tencent_secretid?.has_value ? tencentKeys.tencent_secretid.masked : '输入 SecretId...'" class="secret-key-field" autocomplete="off">
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>SecretKey <span class="field-hint">加密存储，留空保持不变</span></label>
          <input type="password" v-model="tencentForm.secretkey" :placeholder="tencentKeys.tencent_secretkey?.has_value ? tencentKeys.tencent_secretkey.masked : '输入 SecretKey...'" class="secret-key-field" autocomplete="off">
        </div>
        <div class="form-group">
          <label>主持人 UserID <span class="field-hint">创建会议必填</span></label>
          <input type="text" v-model="tencentForm.userid" :placeholder="tencentKeys.tencent_userid?.has_value ? tencentKeys.tencent_userid.masked : '输入主持人 userid...'" class="secret-key-field" autocomplete="off">
        </div>
      </div>
      <button class="btn btn-primary btn-sm" @click="saveTencent" :disabled="tencentSaving">{{ tencentSaving ? '保存中...' : '保存' }}</button>
      <span v-if="tencentMsg" class="save-msg" :class="tencentMsgType">{{ tencentMsg }}</span>
    </BaseAccordion>

    <!-- 角色权限 -->
    <BaseAccordion title="角色权限">
      <table class="config-table">
        <thead><tr><th>角色</th><th>可见菜单</th><th>数据范围</th><th>操作权限</th></tr></thead>
        <tbody>
          <tr v-for="(rp, i) in rolePermissions" :key="i">
            <td><span class="role-badge" :class="rp.badgeClass" :style="rp.style">{{ rp.role }}</span></td>
            <td>{{ rp.menus }}</td>
            <td>{{ rp.dataScope }}</td>
            <td>{{ rp.ops }}</td>
          </tr>
        </tbody>
      </table>
    </BaseAccordion>

    <!-- 操作日志 -->
    <BaseAccordion title="操作日志">
      <table class="config-table">
        <thead><tr><th>时间</th><th>操作人</th><th>模块</th><th>动作</th><th>详情</th></tr></thead>
        <tbody>
          <tr v-for="(log, i) in auditLogs" :key="log.id || i">
            <td>{{ log.time }}</td>
            <td>{{ log.user }}</td>
            <td>{{ log.module }}</td>
            <td>{{ log.action }}</td>
            <td>{{ log.detail }}</td>
          </tr>
        </tbody>
      </table>
      <div class="table-count">共 {{ auditLogs.length }} 条操作记录（最近 {{ auditLogs.length }} 条）</div>
    </BaseAccordion>

    <!-- 添加/编辑邮箱弹窗 -->
    <Teleport to="body">
      <div class="modal-overlay" :class="{ open: showEmailModal }" v-if="showEmailModal" @click.self="showEmailModal = false">
        <div class="modal-box" style="width:560px">
          <h3>{{ editingEmail ? '编辑邮箱' : '添加收简历邮箱' }}</h3>
          <div class="form-row">
            <div class="form-group"><label>邮箱地址 *</label><input type="email" v-model="emailForm.addr" placeholder="hr-recruit@company.com" @blur="onEmailAddrBlur"><div v-if="emailAddrError" class="field-error">{{ emailAddrError }}</div></div>
            <div class="form-group"><label>邮箱类型 *</label><select v-model="emailForm.type" @change="onEmailTypeChange"><option value="">请选择...</option><option value="qq">QQ 邮箱</option><option value="163">163 邮箱</option><option value="gmail">Gmail</option><option value="corp">企业邮箱（Exchange）</option><option value="custom">自定义</option></select></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>接收协议</label><select v-model="emailForm.proto"><option>IMAP（推荐）</option><option>POP3</option></select></div>
            <div class="form-group"><label>端口号</label><input type="text" v-model="emailForm.port" placeholder="993"></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>收件服务器</label><input type="text" v-model="emailForm.server" placeholder="imap.company.com"></div>
            <div class="form-group"><label>加密方式</label><select v-model="emailForm.ssl"><option>SSL/TLS</option><option>STARTTLS</option><option>无</option></select></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>邮箱账号</label><input type="text" v-model="emailForm.user" placeholder="通常与邮箱地址相同"></div>
            <div class="form-group"><label>{{ editingEmail ? '密码/授权码（留空不修改）' : '密码/授权码 *' }}</label><input type="password" v-model="emailForm.pass" placeholder="QQ/Gmail 需使用授权码"><div class="field-hint">QQ邮箱→设置→账户→POP3/SMTP→生成授权码</div></div>
          </div>
          <div class="form-row">
            <div class="form-group"><label>同步周期</label><select v-model="emailForm.freq"><option>每 30 分钟</option><option>每 15 分钟</option><option>每 60 分钟</option><option>每 2 小时</option><option>每天</option></select></div>
            <div class="form-group">
              <label>监控文件夹 <span class="field-hint">只扫描此文件夹</span></label>
              <select v-model="emailForm.folder" @change="onFolderChange" style="width:100%"><option value="INBOX">INBOX（收件箱）</option><option value="custom">自定义文件夹…</option></select>
              <input v-if="emailForm.folder === 'custom'" type="text" v-model="emailForm.folderCustom" placeholder="输入 IMAP 文件夹名，如：招聘简历" style="margin-top:6px;width:100%;padding:7px 10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px">
            </div>
          </div>
          <div style="margin-bottom:14px;font-size:12px">
            <label style="display:flex;align-items:center;gap:6px;cursor:pointer"><input type="checkbox" v-model="emailForm.markRead"> 同步后标记为已读</label>
            <label style="display:flex;align-items:center;gap:6px;margin-top:4px;cursor:pointer"><input type="checkbox" v-model="emailForm.autoReply"> 自动回复（收到简历后发送确认回执）</label>
          </div>
          <div class="modal-actions">
            <button class="btn btn-ghost btn-sm" @click="showEmailModal = false">取消</button>
            <button class="btn btn-outline btn-sm" @click="testConnection" :disabled="emailSaving">{{ emailSaving ? '测试中...' : '测试连接' }}</button>
            <button class="btn btn-primary btn-sm" @click="submitEmail" :disabled="emailSaving">{{ emailSaving ? '保存中...' : (editingEmail ? '保存修改' : '确认添加') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 渠道新增弹窗 -->
    <Teleport to="body">
      <div class="modal-overlay" :class="{ open: showChanModal }" v-if="showChanModal" @click.self="showChanModal = false">
        <div class="modal-box" style="width:400px">
          <h3>新增渠道</h3>
          <div class="form-group"><label>渠道名称 *</label><input type="text" v-model="chanForm.name" placeholder="如：前程无忧"></div>
          <div class="form-group"><label>渠道类型</label><select v-model="chanForm.type"><option>第三方平台</option><option>官网渠道</option><option>内部渠道</option></select></div>
          <div class="form-group"><label>月均费用</label><input type="text" v-model="chanForm.cost" placeholder="¥0"></div>
          <div class="modal-actions" style="margin-top:16px">
            <button class="btn btn-ghost btn-sm" @click="showChanModal = false">取消</button>
            <button class="btn btn-primary btn-sm" @click="submitChannel">确认添加</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 通知模板编辑弹窗 -->
    <Teleport to="body">
      <div class="modal-overlay" :class="{ open: showTplModal }" v-if="showTplModal" @click.self="showTplModal = false">
        <div class="modal-box" style="width:500px">
          <h3>{{ editingTpl && editingTpl.id ? '编辑模板' : '新增模板' }}</h3>
          <div class="form-group"><label>模板名称 *</label><input type="text" v-model="tplForm.name"></div>
          <div class="form-group"><label>类型</label><select v-model="tplForm.type"><option>面试</option><option>Offer</option><option>淘汰</option><option>提醒</option><option>通用</option></select></div>
          <div class="form-group"><label>发送方式</label><input type="text" v-model="tplForm.method" placeholder="飞书 / 短信 / 邮件 / 飞书 + 短信"></div>
          <div class="form-group"><label>标题</label><input type="text" v-model="tplForm.subject" placeholder="支持 {{变量}} 占位"></div>
          <div class="form-group"><label>正文</label><textarea v-model="tplForm.body" rows="4" style="width:100%;padding:8px;border:1px solid var(--c-border);border-radius:6px;font-size:13px;resize:vertical" placeholder="支持 {{变量}} 占位"></textarea></div>
          <div class="modal-actions" style="margin-top:16px">
            <button class="btn btn-ghost btn-sm" @click="showTplModal = false">取消</button>
            <button class="btn btn-primary btn-sm" @click="submitTemplate">保存</button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div class="modal-overlay" :class="{ open: showPreviewModal }" v-if="showPreviewModal" @click.self="showPreviewModal = false">
        <div class="modal-box" style="width:560px">
          <h3>模板预览</h3>
          <div class="tpl-preview-meta">
            <span>{{ previewTpl?.name || '通知模板' }}</span>
            <span>{{ previewTpl?.type || '通用' }}</span>
            <span>{{ previewTpl?.method || '邮件' }}</span>
          </div>
          <div class="tpl-preview-card">
            <div class="tpl-preview-subject">{{ previewSubject }}</div>
            <div class="tpl-preview-body">{{ previewBody }}</div>
          </div>
          <div class="tpl-preview-vars">
            样例变量：候选人=张三，公司=XX公司，岗位=高级Java工程师，HR=李HR
          </div>
          <div class="modal-actions" style="margin-top:16px">
            <button class="btn btn-primary btn-sm" @click="showPreviewModal = false">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>
  </WorkbenchLayout>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import StatCardRow from '../components/StatCardRow.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { KPI_ICONS } from '../components/kpiIcons.js';
import { useToast } from '../composables/useToast.js';
import { useAppError } from '../composables/useAppError.js';
import { api } from '../api/index.js';
import {
  fetchEmailAccounts, fetchChannels, fetchScoreRules, fetchNotifyTemplates,
  fetchRolePermissions, fetchAuditLogs, fetchApiKeys, saveApiKeys, testApiKey, fetchTencentStatus, fetchFeishuStatus,
  createEmailAccount, updateEmailAccount, deleteEmailAccount, resolveEmailServer,
  createChannel, updateChannel,
  updateScoreRules,
  createNotifyTemplate, updateNotifyTemplate,
  fetchKnowledgeBase, updateKnowledgeBase,
} from '../api/config.js';

const { toast } = useToast();
const { handleError } = useAppError();

const emailAccounts = ref([]);
const channels = ref([]);
const scoreRules = reactive({
  profileWeight: 0.10, matchWeight: 0.90,
  decay30: 1.0, decay90: 0.85, decayOver90: 0.70,
  passLine: 60, topCount: 5, searchRange: '近 3 个月',
});
const notifyTemplates = ref([]);
const knowledgeForm = reactive({
  companyName: 'XX公司',
  industry: '',
  website: '',
  companyProfile: '',
  hiringPrinciples: '',
  aiContext: '',
});
const knowledgeSaving = ref(false);
const rolePermissions = ref([]);
const auditLogs = ref([]);
const secretKeys = ref([]);
const tencentKeys = ref({});
const tencentStatus = ref({ configured: false });
const tencentForm = reactive({ appid: '', secretid: '', secretkey: '', userid: '' });
const tencentSaving = ref(false);
const tencentMsg = ref('');
const tencentMsgType = ref('ok');
const feishuKeys = ref({});
const feishuStatus = ref({ configured: false });
const feishuForm = reactive({ appId: '', appSecret: '' });
const feishuShowSecret = ref(false);
const feishuSaving = ref(false);
const feishuTesting = ref(false);
const feishuMsg = ref('');
const feishuMsgType = ref('ok');
const feishuTest = reactive({ status: '', message: '', source: null });

const DEFAULT_NOTIFY_TEMPLATES = [
  {
    id: 'default_interview',
    name: '面试邀请模板',
    type: '面试',
    method: '邮件 + 飞书',
    subject: 'XX公司{{岗位}}面试邀请',
    body: '{{候选人}}您好，诚邀您参加{{公司}}{{岗位}}的面试，请按{{时间}}准时参加。',
    updated: '系统默认',
  },
  {
    id: 'default_offer',
    name: '录用通知模板',
    type: 'Offer',
    method: '邮件',
    subject: 'XX公司{{岗位}}录用通知',
    body: '{{候选人}}您好，恭喜您通过{{公司}}{{岗位}}面试。请查看录用通知并在有效期内确认。',
    updated: '系统默认',
  },
  {
    id: 'default_reject',
    name: '淘汰通知模板',
    type: '淘汰',
    method: '邮件',
    subject: 'XX公司{{岗位}}招聘流程通知',
    body: '{{候选人}}您好，感谢您参与{{公司}}{{岗位}}招聘流程。经综合评估，本次暂不继续推进。',
    updated: '系统默认',
  },
];

const showEmailModal = ref(false);
const showChanModal = ref(false);
const showTplModal = ref(false);
const showPreviewModal = ref(false);
const editingEmail = ref(null);
const editingTpl = ref(null);
const previewTpl = ref(null);
const emailSaving = ref(false);
const ruleSaving = ref(false);
const ruleMsg = ref('');
const ruleMsgType = ref('ok');

const statCards = computed(() => [
  { key: 'email', label: '邮箱账号', value: emailAccounts.value.length, hint: emailAccounts.value.filter(a => a.status === '异常').length + ' 个异常', icon: KPI_ICONS.mail },
  { key: 'channel', label: '渠道配置', value: channels.value.length, hint: channels.value.filter(c => c.status === '启用').length + ' 个启用', icon: KPI_ICONS.briefcase },
  { key: 'template', label: '通知模板', value: notifyTemplates.value.length, hint: '最近更新: ' + (notifyTemplates.value[0]?.updated || '—'), icon: KPI_ICONS.bell },
  { key: 'role', label: '角色权限', value: rolePermissions.value.length, hint: '权限分组', icon: KPI_ICONS.users },
]);

const emailAddrError = ref('');
const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
// 是否已通过 MX 检测自动填充（检测后类型下拉不再强制覆盖服务器配置）
const emailServerDetected = ref(false);

const emailForm = reactive({
  addr: '', type: '', proto: 'IMAP（推荐）', port: '993',
  server: '', ssl: 'SSL/TLS', user: '', pass: '',
  freq: '每 30 分钟', folder: 'INBOX', folderCustom: '',
  markRead: false, autoReply: false,
});
const chanForm = reactive({ name: '', type: '第三方平台', cost: '¥0' });
const tplForm = reactive({ name: '', type: '面试', method: '飞书', subject: '', body: '' });
const previewSubject = computed(() => renderTemplateText(previewTpl.value?.subject || previewTpl.value?.name || '来自XX公司的招聘通知'));
const previewBody = computed(() => renderTemplateText(previewTpl.value?.body || defaultTemplateBody(previewTpl.value)));

import { EMAIL_PRESETS } from '../data/config.js';

// 监听邮箱地址变化，自动检测服务商
let _detectTimer = null;
watch(() => emailForm.addr, (newAddr) => {
  emailAddrError.value = '';
  emailServerDetected.value = false;
  if (!newAddr || !newAddr.includes('@')) return;
  clearTimeout(_detectTimer);
  _detectTimer = setTimeout(() => autoDetectEmail(), 600);
});

function validateEmailAddr() {
  const addr = (emailForm.addr || '').trim();
  if (!addr) { emailAddrError.value = '请填写邮箱地址'; return false; }
  if (!EMAIL_REGEX.test(addr)) { emailAddrError.value = '邮箱格式不正确，请检查'; return false; }
  emailAddrError.value = '';
  return true;
}

function onEmailAddrBlur() {
  // 失焦即校验并触发 MX 检测（填完邮箱必须检测，不靠类型下拉死定义服务器）
  if (!emailForm.addr || !emailForm.addr.includes('@')) return;
  clearTimeout(_detectTimer);
  if (validateEmailAddr()) autoDetectEmail();
}

// 邮箱类型：后端 mail_type 可能是代码（qq/163/...）或历史中文标签，双向映射
const EMAIL_TYPE_LABELS = { qq: 'QQ 邮箱', '163': '163 邮箱', gmail: 'Gmail', corp: '企业邮箱（Exchange）', custom: '自定义' };
const EMAIL_TYPE_CODES = { 'QQ 邮箱': 'qq', '163 邮箱': '163', 'Gmail': 'gmail', '企业邮箱': 'corp', '企业邮箱（Exchange）': 'corp', '自定义': 'custom' };
function emailTypeLabel(t) { return EMAIL_TYPE_LABELS[t] || t || '—'; }
function emailTypeCode(t) { return EMAIL_TYPE_LABELS[t] ? t : (EMAIL_TYPE_CODES[t] || ''); }

const normalizeStatus = (s) => s === '启用' || s === 1 || s === '1' || s === true;
const reverseStatus = (s) => normalizeStatus(s) ? 0 : 1;

async function loadAll() {
  try {
    const [emails, chs, rules, notifs, roles, logs, keys, tStatus, fStatus, kb] = await Promise.all([
      fetchEmailAccounts(), fetchChannels(), fetchScoreRules(),
      fetchNotifyTemplates(), fetchRolePermissions(), fetchAuditLogs(),
      fetchApiKeys(), fetchTencentStatus(), fetchFeishuStatus(), fetchKnowledgeBase(),
    ]);
    if (emails) emailAccounts.value = emails;
    if (chs) channels.value = chs;
    if (rules) Object.assign(scoreRules, rules);
    notifyTemplates.value = Array.isArray(notifs) && notifs.length
      ? notifs
      : DEFAULT_NOTIFY_TEMPLATES.map(t => ({ ...t }));
    if (roles) rolePermissions.value = roles;
    if (logs) auditLogs.value = logs;
    if (kb) Object.assign(knowledgeForm, kb);
    if (keys) {
      secretKeys.value = Object.values(keys)
        .filter(k => !k.key_name.startsWith('tencent_') && k.key_name !== 'feishu' && k.key_name !== 'feishu_app_id')
        .map(k => ({ ...k, inputValue: '', showInput: false, saving: false, testing: false, testStatus: '', testMessage: '', testSource: null }));
      tencentKeys.value = Object.fromEntries(
        Object.entries(keys).filter(([name]) => name.startsWith('tencent_')),
      );
      feishuKeys.value = Object.fromEntries(
        Object.entries(keys).filter(([name]) => name === 'feishu' || name === 'feishu_app_id'),
      );
    }
    if (tStatus) tencentStatus.value = tStatus;
    if (fStatus) feishuStatus.value = fStatus;
  } catch (e) {
    console.warn('Config API fallback:', e.message);
    if (!notifyTemplates.value.length) {
      notifyTemplates.value = DEFAULT_NOTIFY_TEMPLATES.map(t => ({ ...t }));
    }
  }
}

onMounted(() => { loadAll(); });

// ── API Keys ──
async function saveApiKey(keyInfo) {
  if (!keyInfo.inputValue || keyInfo.inputValue === keyInfo.masked) return;
  keyInfo.saving = true;
  try {
    await saveApiKeys({ [keyInfo.key_name]: keyInfo.inputValue });
    toast.success(`${keyInfo.label} 已保存`);
    keyInfo.inputValue = '';
    keyInfo.showInput = false;
    await loadAll();
    // 保存后立即做连通性测试，让用户知道密钥是否可用
    const row = secretKeys.value.find(k => k.key_name === keyInfo.key_name);
    if (row) await runApiKeyTest(row);
  } catch (e) {
    toast.error('保存失败: ' + e.message);
  } finally {
    keyInfo.saving = false;
  }
}

async function runApiKeyTest(keyInfo) {
  keyInfo.testing = true;
  keyInfo.testStatus = 'testing';
  keyInfo.testMessage = '正在测试连接...';
  keyInfo.testSource = null;
  try {
    const res = await testApiKey(keyInfo.key_name);
    const r = (res && res.data) ? res.data : res;
    if (r && r.ok) {
      keyInfo.testStatus = 'ok';
      keyInfo.testMessage = r.message || '连接成功，密钥可用';
      keyInfo.testSource = r.source || null;
      toast.success(`${keyInfo.label} 连接测试成功`);
    } else {
      keyInfo.testStatus = 'fail';
      keyInfo.testMessage = (r && r.message) || '连接失败';
      keyInfo.testSource = (r && r.source) || null;
      toast.warning(`${keyInfo.label}：${keyInfo.testMessage}`);
    }
  } catch (e) {
    keyInfo.testStatus = 'fail';
    keyInfo.testMessage = '测试请求失败: ' + e.message;
    keyInfo.testSource = null;
  } finally {
    keyInfo.testing = false;
  }
}

// ── Tencent Meeting ──
async function saveTencent() {
  const payload = {};
  if (tencentForm.appid.trim()) payload.tencent_appid = tencentForm.appid.trim();
  if (tencentForm.secretid.trim()) payload.tencent_secretid = tencentForm.secretid.trim();
  if (tencentForm.secretkey.trim()) payload.tencent_secretkey = tencentForm.secretkey.trim();
  if (tencentForm.userid.trim()) payload.tencent_userid = tencentForm.userid.trim();
  if (!Object.keys(payload).length) {
    toast.warning('请至少填写一项凭证');
    return;
  }
  tencentSaving.value = true;
  tencentMsg.value = '';
  try {
    await saveApiKeys(payload);
    toast.success('腾讯会议凭证已保存');
    Object.assign(tencentForm, { appid: '', secretid: '', secretkey: '', userid: '' });
    await loadAll();
    tencentMsg.value = tencentStatus.value.configured ? '凭证齐全，已启用' : '已保存，凭证尚不完整';
    tencentMsgType.value = tencentStatus.value.configured ? 'ok' : 'error';
  } catch (e) {
    toast.error('保存失败: ' + e.message);
  } finally {
    tencentSaving.value = false;
  }
}

// ── Feishu ──
async function saveFeishu() {
  const payload = {};
  if (feishuForm.appId.trim()) payload.feishu_app_id = feishuForm.appId.trim();
  if (feishuForm.appSecret.trim()) payload.feishu = feishuForm.appSecret.trim();
  if (!Object.keys(payload).length) {
    toast.warning('请至少填写一项凭证');
    return;
  }
  feishuSaving.value = true;
  feishuMsg.value = '';
  try {
    await saveApiKeys(payload);
    toast.success('飞书凭证已保存');
    Object.assign(feishuForm, { appId: '', appSecret: '' });
    feishuShowSecret.value = false;
    await loadAll();
    feishuMsg.value = feishuStatus.value.configured ? '凭证齐全，已启用' : '已保存，App ID 与 App Secret 需配对填写';
    feishuMsgType.value = feishuStatus.value.configured ? 'ok' : 'error';
    if (feishuStatus.value.configured) await testFeishu();
  } catch (e) {
    toast.error('保存失败: ' + e.message);
  } finally {
    feishuSaving.value = false;
  }
}

async function testFeishu() {
  feishuTesting.value = true;
  Object.assign(feishuTest, { status: 'testing', message: '正在测试连接...', source: null });
  try {
    const res = await testApiKey('feishu');
    const r = (res && res.data) ? res.data : res;
    if (r && r.ok) {
      Object.assign(feishuTest, { status: 'ok', message: r.message || '连接成功，密钥可用', source: r.source || null });
      toast.success('飞书连接测试成功');
    } else {
      Object.assign(feishuTest, { status: 'fail', message: (r && r.message) || '连接失败', source: (r && r.source) || null });
      toast.warning(`飞书：${feishuTest.message}`);
    }
  } catch (e) {
    Object.assign(feishuTest, { status: 'fail', message: '测试请求失败: ' + e.message, source: null });
  } finally {
    feishuTesting.value = false;
  }
}

// ── Email ──
function openAddEmail() {
  editingEmail.value = null;
  emailAddrError.value = '';
  emailServerDetected.value = false;
  Object.assign(emailForm, {
    addr: '', type: '', proto: 'IMAP（推荐）', port: '993',
    server: '', ssl: 'SSL/TLS', user: '', pass: '',
    freq: '每 30 分钟', folder: 'INBOX', folderCustom: '',
    markRead: false, autoReply: false,
  });
  showEmailModal.value = true;
}
function editEmail(acct) {
  editingEmail.value = acct;
  emailAddrError.value = '';
  emailServerDetected.value = false;
  Object.assign(emailForm, {
    addr: acct.address || '', type: emailTypeCode(acct.type),
    proto: acct.proto || 'IMAP（推荐）', port: acct.port || '993',
    server: acct.server || '', ssl: acct.ssl || 'SSL/TLS',
    user: acct.address || '', pass: '',
    freq: acct.freq || '每 30 分钟', folder: acct.folder || 'INBOX',
    folderCustom: '', markRead: false, autoReply: false,
  });
  showEmailModal.value = true;
}
async function testEmailConn(acct) {
  if (!acct || !acct.id) return;
  emailSaving.value = true;
  try {
    const r = await updateEmailAccount(acct.id, { __test_conn: true });
    if (r && r.test_ok) {
      toast.success('IMAP 连接成功：' + (acct.server || '—') + ':' + (acct.port || '993'));
    } else {
      toast.error('连接失败：' + ((r && r.test_msg) || '未知错误'));
    }
  } catch (e) {
    toast.error('连接失败: ' + e.message);
  }
  emailSaving.value = false;
  await loadAll();
}
async function deleteEmail(acct) {
  if (!confirm(`确定删除邮箱 ${acct.address} 吗？`)) return;
  try {
    await deleteEmailAccount(acct.id);
  } catch (e) {
    toast.error('删除失败: ' + e.message);
  }
  await loadAll();
}
function onEmailTypeChange() {
  // 类型下拉降级为手动覆盖：已通过 MX 检测出服务器时不再强制覆盖
  if (emailServerDetected.value && emailForm.server) return;
  const preset = EMAIL_PRESETS[emailForm.type];
  if (preset) {
    if (preset.server || !emailForm.server) emailForm.server = preset.server;
    if (preset.port || !emailForm.port) emailForm.port = preset.port;
    emailForm.proto = preset.proto;
    emailForm.ssl = preset.ssl;
  }
}
async function autoDetectEmail() {
  if (!emailForm.addr) return;
  if (!validateEmailAddr()) return;
  try {
    const d = await resolveEmailServer(emailForm.addr.trim());
    if (d && d.imap_host) {
      // 回填类型显示（仅展示，不反向驱动服务器配置）
      const domain = emailForm.addr.split('@')[1]?.toLowerCase();
      if (domain === 'qq.com') emailForm.type = 'qq';
      else if (domain === '163.com') emailForm.type = '163';
      else if (domain === 'gmail.com') emailForm.type = 'gmail';
      else emailForm.type = 'corp';
      // 自动填服务器信息（MX 检测结果优先于类型预设）
      emailForm.server = d.imap_host;
      emailForm.port = String(d.imap_port || 993);
      emailForm.ssl = d.encryption || 'SSL/TLS';
      emailForm.proto = 'IMAP（推荐）';
      emailServerDetected.value = true;
      toast.info('已识别：' + d.provider + ' → ' + d.imap_host);
    } else {
      emailServerDetected.value = false;
      if (!emailForm.server) toast.warning('未识别到邮箱服务商，请手动填写收件服务器');
    }
  } catch (e) { /* silent */ }
}
function onFolderChange() {
  if (emailForm.folder !== 'custom') emailForm.folderCustom = '';
}
async function testConnection() {
  emailSaving.value = true;
  if (editingEmail.value?.id) {
    try { await updateEmailAccount(editingEmail.value.id, { __test_conn: true }); } catch (e) { /* ignore */ }
  }
  emailSaving.value = false;
  toast.success('连接成功！\n服务器：' + emailForm.server + '\n端口：' + emailForm.port);
}
async function submitEmail() {
  if (!validateEmailAddr()) return;
  if (!editingEmail.value && !emailForm.pass) { toast.warning('请填写密码/授权码'); return; }
  emailSaving.value = true;
  const folder = emailForm.folder === 'custom' ? emailForm.folderCustom : 'INBOX';
  const payload = {
    address: emailForm.addr.trim(), type: emailForm.type, proto: emailForm.proto, port: emailForm.port,
    server: emailForm.server || '', ssl: emailForm.ssl, name: emailForm.user || emailForm.addr,
    pass: emailForm.pass, freq: emailForm.freq, folder,
  };
  try {
    if (editingEmail.value?.id) {
      await updateEmailAccount(editingEmail.value.id, payload);
    } else {
      await createEmailAccount(payload);
    }
  } catch (e) {
    // 失败时保留弹窗与已填内容，避免用户输入丢失
    toast.error('操作失败: ' + e.message);
    emailSaving.value = false;
    return;
  }
  emailSaving.value = false;
  showEmailModal.value = false;
  await loadAll();
}

// ── Channel ──
async function toggleChanStatus(ch) {
  const newStatus = ch.status === '启用' ? '停用' : '启用';
  try {
    await updateChannel(ch.code, { status: newStatus });
  } catch (e) {
    toast.error('操作失败: ' + e.message);
  }
  await loadAll();
}
async function updateChanCost(ch, e) {
  const val = e.target.value.trim();
  if (val === ch.cost) return;
  try {
    await updateChannel(ch.code, { cost: val });
  } catch (err) {
    toast.error('更新失败: ' + err.message);
  }
  await loadAll();
}
async function submitChannel() {
  if (!chanForm.name) { toast.warning('请填写渠道名称'); return; }
  try {
    await createChannel({ name: chanForm.name, type: chanForm.type, cost: chanForm.cost, status: 1 });
  } catch (e) {
    toast.error('新增失败: ' + e.message);
  }
  showChanModal.value = false;
  chanForm.name = ''; chanForm.type = '第三方平台'; chanForm.cost = '¥0';
  await loadAll();
}

// ── Score rules ──
async function saveRules() {
  ruleSaving.value = true; ruleMsg.value = '';
  try {
    const result = await updateScoreRules({ ...scoreRules });
    if (result) {
      ruleMsg.value = '已保存并生效';
      ruleMsgType.value = 'ok';
    }
  } catch (e) {
    ruleMsg.value = '保存失败: ' + e.message;
    ruleMsgType.value = 'error';
  }
  ruleSaving.value = false;
  setTimeout(() => { ruleMsg.value = ''; }, 3000);
}

// ── Template ──
function openAddTemplate() {
  editingTpl.value = null;
  Object.assign(tplForm, { name: '', type: '面试', method: '飞书', subject: '', body: '' });
  showTplModal.value = true;
}
function editTemplate(tpl) {
  editingTpl.value = tpl;
  Object.assign(tplForm, {
    name: tpl.name || '', type: tpl.type || '面试',
    method: tpl.method || '飞书',
    subject: tpl.subject || '', body: tpl.body || '',
  });
  showTplModal.value = true;
}
function previewTemplate(tpl) {
  previewTpl.value = tpl;
  showPreviewModal.value = true;
}

function defaultTemplateBody(tpl) {
  const type = String(tpl?.type || tpl?.name || '');
  if (type.includes('Offer') || type.includes('录用')) {
    return '张三您好，恭喜您通过XX公司的{{岗位}}面试。请查看录用通知并在有效期内确认。';
  }
  if (type.includes('淘汰')) {
    return '张三您好，感谢您参与XX公司的{{岗位}}招聘流程。经综合评估，本次暂不继续推进。';
  }
  if (type.includes('提醒')) {
    return '张三您好，提醒您关注XX公司{{岗位}}招聘流程的下一步安排。';
  }
  return '张三您好，诚邀您参加XX公司{{岗位}}的面试，请按约定时间参加。';
}

function renderTemplateText(text) {
  return String(text || '')
    .replaceAll('{{候选人}}', '张三')
    .replaceAll('{{candidate}}', '张三')
    .replaceAll('{{公司}}', 'XX公司')
    .replaceAll('{{company}}', 'XX公司')
    .replaceAll('{{岗位}}', '高级Java工程师')
    .replaceAll('{{position}}', '高级Java工程师')
    .replaceAll('{{HR}}', '李HR')
    .replaceAll('{{hr}}', '李HR')
    .replaceAll('{{时间}}', '2026-07-23 14:00')
    .replaceAll('{{time}}', '2026-07-23 14:00');
}

async function saveKnowledgeBase() {
  knowledgeSaving.value = true;
  try {
    const r = await updateKnowledgeBase({ ...knowledgeForm });
    if (r?.data) Object.assign(knowledgeForm, r.data);
    toast.success('知识库已保存，AI 功能将使用新的公司上下文');
  } catch (e) {
    toast.error('知识库保存失败: ' + (e.message || '未知错误'));
  } finally {
    knowledgeSaving.value = false;
  }
}

async function updateTplMethod(tpl, e) {
  const val = e.target.value.trim();
  if (val === tpl.method) return;
  try {
    await updateNotifyTemplate(tpl.id, { method: val, name: tpl.name, type: tpl.type, subject: tpl.subject, body: tpl.body });
  } catch (err) {
    toast.error('更新失败: ' + err.message);
  }
  await loadAll();
}
async function submitTemplate() {
  if (!tplForm.name) { toast.warning('请填写模板名称'); return; }
  try {
    if (editingTpl.value?.id) {
      await updateNotifyTemplate(editingTpl.value.id, { ...tplForm });
    } else {
      await createNotifyTemplate({ ...tplForm });
    }
  } catch (e) {
    toast.error('操作失败: ' + e.message);
  }
  showTplModal.value = false;
  await loadAll();
}
</script>

<style scoped>
.admin-only { font-size: 11px; color: var(--c-sub); }
.accordion-desc { font-size: 12px; color: var(--c-sub); margin-bottom: 12px; line-height: 1.8; }
.config-table { font-size: 13px; margin-bottom: 12px; min-width: 100%; }
.config-table th {
  position: sticky; top: 0; height: 36px; padding: 0 12px;
  color: var(--e-muted); background: var(--e-surface-soft);
  border-bottom: 1px solid var(--e-border); font-weight: 650; white-space: nowrap; text-align: left;
}
.tpl-preview-meta { display: flex; gap: 8px; flex-wrap: wrap; margin: 8px 0 12px; }
.tpl-preview-meta span { padding: 4px 8px; border: 1px solid var(--c-border); border-radius: 6px; font-size: 12px; color: var(--c-sub); }
.tpl-preview-card { border: 1px solid var(--c-border); border-radius: 8px; background: var(--c-card); overflow: hidden; }
.tpl-preview-subject { padding: 12px 14px; border-bottom: 1px solid var(--c-border); font-size: 14px; font-weight: 700; color: var(--c-text); }
.tpl-preview-body { white-space: pre-wrap; padding: 14px; font-size: 13px; line-height: 1.8; color: var(--c-body); background: var(--c-bg); }
.tpl-preview-vars { margin-top: 10px; font-size: 12px; color: var(--c-sub); }
.kb-textarea { width:100%; padding:8px 10px; border:1px solid var(--c-border); border-radius:6px; font-size:13px; font-family:inherit; resize:vertical; box-sizing:border-box; }
.config-table td { height: 40px; padding: 0 12px; color: var(--e-ink-2); border-bottom: 1px solid var(--e-border-soft); }
.row-actions { white-space: nowrap; }
.row-actions .btn-text, .row-actions .btn-text-danger { display: inline; padding: 0 4px; font-size: 12px; }
.config-divider { margin: 16px 0; border-color: var(--c-border); }
.field-hint { font-size: 11px; color: var(--c-sub); font-weight: 400; }
.field-error { font-size: 12px; color: var(--c-reject); margin-top: 4px; font-weight: 500; }
.save-msg { margin-left: 12px; font-size: 13px; font-weight: 600; }
.save-msg.ok { color: var(--c-done); }
.save-msg.error { color: var(--c-reject); }
.form-group textarea { font-family: inherit; }
.permission-bar { font-size: 12px; color: var(--c-sub); padding: 6px 0; margin-bottom: 8px; border-bottom: 1px solid var(--c-border-light); }

.secret-key-row {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 0;
  border-bottom: 1px solid var(--c-border-light);
}
.secret-key-row:last-child { border-bottom: none; }
.secret-key-info { flex: 1; min-width: 0; }
.secret-key-label { font-size: 14px; font-weight: 650; color: var(--c-text); margin-bottom: 2px; }
.secret-key-desc { font-size: 12px; color: var(--c-sub); }
.secret-key-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--c-primary);
  text-decoration: none;
}
.secret-key-link:hover { text-decoration: underline; }
.secret-key-input-group { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.secret-key-field {
  width: 260px;
  padding: 8px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  font-size: 13px;
  font-family: monospace;
  background: var(--c-card);
  color: var(--c-text);
}
.secret-key-field::placeholder { color: var(--c-sub); font-family: monospace; }
.secret-key-field:focus { outline: none; border-color: var(--c-primary); }
.secret-key-toggle { font-size: 12px; color: var(--c-sub); cursor: pointer; white-space: nowrap; }
.secret-key-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--c-warn);
  display: flex;
  align-items: center;
  gap: 6px;
}
.secret-key-saving { opacity: .6; pointer-events: none; }
.secret-key-test-result {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
}
.secret-key-test-result svg { width: 14px; height: 14px; flex-shrink: 0; }
.secret-key-test-result.ok { color: var(--c-done); }
.secret-key-test-result.fail { color: var(--c-warn); }
.secret-key-test-result.testing { color: var(--c-sub); }
.secret-key-test-source { color: var(--c-sub); }
.tencent-status { font-weight: 650; }
.tencent-status.ok { color: var(--c-done); }
.tencent-status.off { color: var(--c-warn); }
</style>
