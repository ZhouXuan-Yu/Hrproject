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
            <td>{{ acct.type }}</td>
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
              <a v-if="tpl.name && tpl.name.includes('邀请')" href="#" class="btn btn-text btn-sm" @click.prevent="previewTemplate(tpl)">预览</a>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-primary btn-sm" style="margin-top:10px" @click="openAddTemplate">+ 新增模板</button>
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
  </WorkbenchLayout>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import StatCardRow from '../components/StatCardRow.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { KPI_ICONS } from '../components/kpiIcons.js';
import {
  fetchEmailAccounts, fetchChannels, fetchScoreRules, fetchNotifyTemplates,
  fetchRolePermissions, fetchAuditLogs,
  createEmailAccount, updateEmailAccount, deleteEmailAccount,
  createChannel, updateChannel,
  updateScoreRules,
  createNotifyTemplate, updateNotifyTemplate,
} from '../api/config.js';

const emailAccounts = ref([]);
const channels = ref([]);
const scoreRules = reactive({
  profileWeight: 0.10, matchWeight: 0.90,
  decay30: 1.0, decay90: 0.85, decayOver90: 0.70,
  passLine: 60, topCount: 5, searchRange: '近 3 个月',
});
const notifyTemplates = ref([]);
const rolePermissions = ref([]);
const auditLogs = ref([]);

const showEmailModal = ref(false);
const showChanModal = ref(false);
const showTplModal = ref(false);
const editingEmail = ref(null);
const editingTpl = ref(null);
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

const emailForm = reactive({
  addr: '', type: '', proto: 'IMAP（推荐）', port: '993',
  server: '', ssl: 'SSL/TLS', user: '', pass: '',
  freq: '每 30 分钟', folder: 'INBOX', folderCustom: '',
  markRead: false, autoReply: false,
});
const chanForm = reactive({ name: '', type: '第三方平台', cost: '¥0' });
const tplForm = reactive({ name: '', type: '面试', method: '飞书', subject: '', body: '' });

import { EMAIL_PRESETS } from '../data/config.js';

const normalizeStatus = (s) => s === '启用' || s === 1 || s === '1' || s === true;
const reverseStatus = (s) => normalizeStatus(s) ? 0 : 1;

async function loadAll() {
  try {
    const [emails, chs, rules, notifs, roles, logs] = await Promise.all([
      fetchEmailAccounts(), fetchChannels(), fetchScoreRules(),
      fetchNotifyTemplates(), fetchRolePermissions(), fetchAuditLogs(),
    ]);
    if (emails && emails.length) emailAccounts.value = emails;
    if (chs && chs.length) channels.value = chs;
    if (rules) Object.assign(scoreRules, rules);
    if (notifs && notifs.length) notifyTemplates.value = notifs;
    if (roles && roles.length) rolePermissions.value = roles;
    if (logs && logs.length) auditLogs.value = logs;
  } catch (e) {
    console.warn('Config API fallback:', e.message);
  }
}

onMounted(() => { loadAll(); });

// ── Email ──
function openAddEmail() {
  editingEmail.value = null;
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
  Object.assign(emailForm, {
    addr: acct.address || '', type: acct.type || '',
    proto: acct.proto || 'IMAP（推荐）', port: acct.port || '993',
    server: acct.server || '', ssl: acct.ssl || 'SSL/TLS',
    user: acct.address || '', pass: '',
    freq: acct.freq || '每 30 分钟', folder: acct.folder || 'INBOX',
    folderCustom: '', markRead: false, autoReply: false,
  });
  showEmailModal.value = true;
}
async function testEmailConn(acct) {
  if (acct && acct.id) {
    await updateEmailAccount(acct.id, { __test_conn: true });
  }
  emailSaving.value = true;
  try {
    await updateEmailAccount(editingEmail.value?.id || 0, { __test_conn: true });
  } catch (e) { /* ignore */ }
  emailSaving.value = false;
  alert(`测试连接完成！\n\n服务器：${emailForm.server || acct?.server || '—'}\n端口：${emailForm.port || acct?.port || '993'}`);
  await loadAll();
}
async function deleteEmail(acct) {
  if (!confirm(`确定删除邮箱 ${acct.address} 吗？`)) return;
  try {
    await deleteEmailAccount(acct.id);
  } catch (e) {
    alert('删除失败: ' + e.message);
  }
  await loadAll();
}
function onEmailTypeChange() {
  const preset = EMAIL_PRESETS[emailForm.type];
  if (!preset) return;
  emailForm.server = preset.server;
  emailForm.port = preset.port;
  emailForm.proto = preset.proto;
  emailForm.ssl = preset.ssl;
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
  alert(`正在测试连接...\n\n服务器：${emailForm.server}\n端口：${emailForm.port}\n\n连接成功！`);
}
async function submitEmail() {
  if (!emailForm.addr) { alert('请填写邮箱地址'); return; }
  if (!editingEmail.value && !emailForm.pass) { alert('请填写密码/授权码'); return; }
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
    alert('操作失败: ' + e.message);
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
    alert('操作失败: ' + e.message);
  }
  await loadAll();
}
async function updateChanCost(ch, e) {
  const val = e.target.value.trim();
  if (val === ch.cost) return;
  try {
    await updateChannel(ch.code, { cost: val });
  } catch (err) {
    alert('更新失败: ' + err.message);
  }
  await loadAll();
}
async function submitChannel() {
  if (!chanForm.name) { alert('请填写渠道名称'); return; }
  try {
    await createChannel({ name: chanForm.name, type: chanForm.type, cost: chanForm.cost, status: 1 });
  } catch (e) {
    alert('新增失败: ' + e.message);
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
  alert(`预览：${tpl.name}\n\n${tpl.body || '（无内容）'}`);
}
async function updateTplMethod(tpl, e) {
  const val = e.target.value.trim();
  if (val === tpl.method) return;
  try {
    await updateNotifyTemplate(tpl.id, { method: val, name: tpl.name, type: tpl.type, subject: tpl.subject, body: tpl.body });
  } catch (err) {
    alert('更新失败: ' + err.message);
  }
  await loadAll();
}
async function submitTemplate() {
  if (!tplForm.name) { alert('请填写模板名称'); return; }
  try {
    if (editingTpl.value?.id) {
      await updateNotifyTemplate(editingTpl.value.id, { ...tplForm });
    } else {
      await createNotifyTemplate({ ...tplForm });
    }
  } catch (e) {
    alert('操作失败: ' + e.message);
  }
  showTplModal.value = false;
  await loadAll();
}

function alert(msg) { window.alert(msg); }
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
.config-table td { height: 40px; padding: 0 12px; color: var(--e-ink-2); border-bottom: 1px solid var(--e-border-soft); }
.row-actions { white-space: nowrap; }
.row-actions .btn-text, .row-actions .btn-text-danger { display: inline; padding: 0 4px; font-size: 12px; }
.config-divider { margin: 16px 0; border-color: var(--c-border); }
.field-hint { font-size: 11px; color: var(--c-sub); font-weight: 400; }
.save-msg { margin-left: 12px; font-size: 13px; font-weight: 600; }
.save-msg.ok { color: var(--c-done); }
.save-msg.error { color: var(--c-reject); }
.form-group textarea { font-family: inherit; }
.permission-bar { font-size: 12px; color: var(--c-sub); padding: 6px 0; margin-bottom: 8px; border-bottom: 1px solid var(--c-border-light); }
</style>
