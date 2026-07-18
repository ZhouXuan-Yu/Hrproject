<template>
  <WorkbenchLayout title="招聘基础配置" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <span class="admin-only">仅管理员可见</span>
    </template>

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
          <tr v-for="(acct, i) in emailAccounts" :key="i">
            <td>{{ acct.address }}</td>
            <td>{{ acct.type }}</td>
            <td>{{ acct.freq }}</td>
            <td><span :style="{ color: acct.statusColor === 'done' ? 'var(--c-done)' : 'var(--c-warn)', fontWeight: 600 }">{{ acct.status }}</span></td>
            <td>{{ acct.lastSync }}</td>
            <td class="row-actions">
              <a href="#" class="btn btn-text btn-sm" @click.prevent>{{ acct.status === '异常' ? '重新连接' : '测试连接' }}</a>
              <a href="#" class="btn btn-text btn-sm" @click.prevent>编辑</a>
              <a href="#" class="btn btn-text-danger btn-sm" @click.prevent>删除</a>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-primary btn-sm" @click="showEmailModal = true">+ 添加邮箱账号</button>
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
          <tr v-for="(ch, i) in channels" :key="i">
            <td>{{ ch.code }}</td>
            <td>{{ ch.name }}</td>
            <td>{{ ch.type }}</td>
            <td>{{ ch.cost }}</td>
            <td><StatusBadge type="done">{{ ch.status }}</StatusBadge></td>
            <td class="row-actions">
              <a href="#" class="btn btn-text btn-sm" @click.prevent>编辑</a>
              <a href="#" class="btn btn-text-danger btn-sm" @click.prevent>停用</a>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-primary btn-sm" @click="alert('功能开发中')">+ 新增渠道</button>
    </BaseAccordion>

    <!-- 打分规则 -->
    <BaseAccordion title="打分规则配置">
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
      <button class="btn btn-primary btn-sm" @click="saveRules">保存规则</button>
    </BaseAccordion>

    <!-- 通知模板 -->
    <BaseAccordion title="通知模板">
      <table class="config-table">
        <thead><tr><th>模板名称</th><th>类型</th><th>发送方式</th><th>最近更新</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(tpl, i) in notifyTemplates" :key="i">
            <td>{{ tpl.name }}</td>
            <td>{{ tpl.type }}</td>
            <td>{{ tpl.method }}</td>
            <td>{{ tpl.updated }}</td>
            <td class="row-actions">
              <a href="#" class="btn btn-text btn-sm" @click.prevent>编辑</a>
              <a v-if="tpl.name.includes('邀请')" href="#" class="btn btn-text btn-sm" @click.prevent>预览</a>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-primary btn-sm" style="margin-top:10px" @click="alert('功能开发中')">+ 新增模板</button>
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
          <tr v-for="(log, i) in auditLogs" :key="i">
            <td>{{ log.time }}</td>
            <td>{{ log.user }}</td>
            <td>{{ log.module }}</td>
            <td>{{ log.action }}</td>
            <td>{{ log.detail }}</td>
          </tr>
        </tbody>
      </table>
      <div class="table-count">共 {{ auditLogs.length }} 条操作记录（最近 1 天）</div>
    </BaseAccordion>

    <!-- 添加邮箱弹窗 -->
    <Teleport to="body">
      <div class="modal-overlay" :class="{ open: showEmailModal }" v-if="showEmailModal" @click.self="showEmailModal = false">
        <div class="modal-box" style="width:560px">
          <h3>添加收简历邮箱</h3>
          <div class="form-row">
            <div class="form-group"><label>邮箱地址 *</label><input type="email" v-model="emailForm.addr" placeholder="hr-recruit@company.com"></div>
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
            <div class="form-group"><label>密码/授权码 *</label><input type="password" v-model="emailForm.pass" placeholder="QQ/Gmail 需使用授权码"><div class="field-hint">QQ邮箱→设置→账户→POP3/SMTP→生成授权码</div></div>
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
            <button class="btn btn-outline btn-sm" @click="testConnection">测试连接</button>
            <button class="btn btn-primary btn-sm" @click="submitEmail">确认添加</button>
          </div>
        </div>
      </div>
    </Teleport>
  </WorkbenchLayout>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import { EMAIL_ACCOUNTS, CHANNELS, SCORE_RULES, NOTIFY_TEMPLATES, ROLE_PERMISSIONS, AUDIT_LOGS, EMAIL_PRESETS } from '../data/config.js';
import { fetchEmailAccounts, fetchChannels, fetchScoreRules, fetchNotifyTemplates, fetchRolePermissions, fetchAuditLogs } from '../api/config.js';

const emailAccounts = ref(EMAIL_ACCOUNTS);
const channels = ref(CHANNELS);
const scoreRules = reactive({ ...SCORE_RULES });
const notifyTemplates = ref(NOTIFY_TEMPLATES);
const rolePermissions = ref(ROLE_PERMISSIONS);
const auditLogs = ref(AUDIT_LOGS);
const showEmailModal = ref(false);

const emailForm = reactive({
  addr: '', type: '', proto: 'IMAP（推荐）', port: '993',
  server: '', ssl: 'SSL/TLS', user: '', pass: '',
  freq: '每 30 分钟', folder: 'INBOX', folderCustom: '',
  markRead: false, autoReply: false,
});

async function loadFromApi() {
  try {
    const [apiEmail, apiChannels, apiRules, apiNotify, apiRoles, apiLogs] = await Promise.all([
      fetchEmailAccounts(),
      fetchChannels(),
      fetchScoreRules(),
      fetchNotifyTemplates(),
      fetchRolePermissions(),
      fetchAuditLogs(),
    ]);
    if (apiEmail && apiEmail.length) emailAccounts.value = apiEmail;
    if (apiChannels && apiChannels.length) channels.value = apiChannels;
    if (apiRules) Object.assign(scoreRules, apiRules);
    if (apiNotify && apiNotify.length) notifyTemplates.value = apiNotify;
    if (apiRoles && apiRoles.length) rolePermissions.value = apiRoles;
    if (apiLogs && apiLogs.length) auditLogs.value = apiLogs;
  } catch (e) {
    console.warn('API fallback to mock:', e.message);
  }
}

onMounted(() => { loadFromApi(); });

function onEmailTypeChange(){
  const preset = EMAIL_PRESETS[emailForm.type];
  if (!preset) return;
  emailForm.server = preset.server;
  emailForm.port = preset.port;
  emailForm.proto = preset.proto;
  emailForm.ssl = preset.ssl;
}

function onFolderChange(){
  if (emailForm.folder !== 'custom') emailForm.folderCustom = '';
}

function testConnection(){
  alert(`正在测试连接...\n\n服务器：${emailForm.server}\n端口：${emailForm.port}\n\n连接成功！`);
}

function submitEmail(){
  if (!emailForm.addr || !emailForm.pass){ alert('请填写邮箱地址和密码/授权码'); return; }
  const folder = emailForm.folder === 'custom' ? emailForm.folderCustom : 'INBOX';
  alert(`邮箱已添加！\n\n地址：${emailForm.addr}\n监控文件夹：${folder}\n同步周期：${emailForm.freq}\n\n系统将自动测试连接并开始首次同步\n解析的简历将自动入库「人才库-简历储备库」`);
  showEmailModal.value = false;
}

function saveRules(){
  alert('评分规则已保存！');
}

function alert(msg){ window.alert(msg); }
</script>

<style scoped>
.admin-only {
  font-size: 11px;
  color: var(--c-sub);
}
.accordion-desc {
  font-size: 12px;
  color: var(--c-sub);
  margin-bottom: 12px;
  line-height: 1.8;
}
.config-table {
  font-size: 13px;
  margin-bottom: 12px;
  min-width: 100%;
}
.config-table th {
  position: sticky;
  top: 0;
  height: 36px;
  padding: 0 12px;
  color: var(--e-muted);
  background: var(--e-surface-soft);
  border-bottom: 1px solid var(--e-border);
  font-weight: 650;
  white-space: nowrap;
  text-align: left;
}
.config-table td {
  height: 40px;
  padding: 0 12px;
  color: var(--e-ink-2);
  border-bottom: 1px solid var(--e-border-soft);
}
.row-actions {
  white-space: nowrap;
}
.row-actions .btn-text, .row-actions .btn-text-danger {
  display: inline;
  padding: 0 4px;
  font-size: 12px;
}
.config-divider {
  margin: 16px 0;
  border-color: var(--c-border);
}
.field-hint {
  font-size: 11px;
  color: var(--c-sub);
  font-weight: 400;
}
</style>
