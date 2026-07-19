<template>
  <WorkbenchLayout title="面试计划" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <div style="position:relative">
        <button class="btn btn-ghost btn-sm" id="alertBtn" @click="showAlerts = !showAlerts" style="gap:4px">
          <svg viewBox="0 0 24 24" style="width:16px;height:16px;stroke:var(--c-warn);fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          提醒 <span class="alert-badge">4</span>
        </button>
        <div id="alertDropdown" v-if="showAlerts" style="display:block;position:absolute;top:100%;right:0;margin-top:6px;width:380px;background:var(--c-card);border:1px solid var(--c-border);border-radius:12px;padding:16px;box-shadow:0 8px 32px rgba(0,0,0,.12);z-index:100;font-size:13px;line-height:2">
          <div style="font-weight:700;margin-bottom:10px;color:var(--c-text);font-size:14px">智能提醒</div>
          <div v-for="(alert, i) in ALERTS_SOURCE" :key="i" style="display:flex;align-items:center;justify-content:space-between;padding:6px 0">
            <span><span class="alert-dot" :class="alert.type"></span> {{ alert.text }}</span>
            <button v-if="alert.actionMsg" class="btn btn-primary btn-sm" @click="doAlert(alert.actionMsg)">{{ alert.action }}</button>
            <button v-else class="btn btn-outline btn-sm" @click="showAlerts = false">{{ alert.action }}</button>
          </div>
        </div>
      </div>
      <select v-if="!isInterviewerRole" id="scopeSelect" v-model="currentScope" @change="onScopeChange" style="height:30px;padding:0 10px;border:1px solid var(--c-border);border-radius:6px;font-size:12px;font-family:inherit;background:var(--c-card);color:var(--c-body)">
        <option value="all">全部面试</option>
        <option value="created">我发起的</option>
      </select>
      <button v-if="!isInterviewerRole" class="btn btn-primary btn-sm" @click="openGlobalScheduleModal('','','')" style="margin-left:8px">+ 新建面试</button>
      <button class="btn btn-outline btn-sm" @click="showCalendar = true" style="margin-left:4px" title="查看本周面试日程">
        <svg viewBox="0 0 24 24" style="width:14px;height:14px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg> 日程
      </button>
    </template>

    <!-- Pipeline status chips — compact filter for list tab -->
    <div data-slot="iv-pipeline-row">
      <button v-for="kpi in kpis" :key="kpi.key"
        data-slot="iv-pipeline-chip"
        :data-active="listStatus === kpi.key"
        @click="listStatus = listStatus === kpi.key ? 'all' : kpi.key"
      >
        <i class="iv-dot" :style="{ background: stageColor(kpi.key) }"></i>
        <span data-slot="iv-pipeline-label">{{ kpi.label }}</span>
        <span data-slot="iv-pipeline-val">{{ kpi.value }}</span>
      </button>
    </div>
    <!-- Block hero-page-summary (redundant "当前筛选范围" label) — let hero-page-command/workspace inject from app.js -->
    <section class="hero-page-summary" style="display:none" aria-hidden="true"></section>

    <!-- Tabs -->
    <div class="tabs" role="tablist">
      <button v-for="tab in visibleTabs" :key="tab.id"
        class="tab" :class="{ active: activeTab === tab.id }"
        :aria-selected="activeTab === tab.id ? 'true' : 'false'"
        role="tab" @click="activeTab = tab.id"
      >{{ tab.label }}</button>
    </div>

    <!-- 全部面试 panel -->
    <div class="tab-panel" :class="{ active: activeTab === 'list' }">
      <div class="table-wrap">
        <table><thead><tr><th>候选人</th><th>岗位</th><th>轮次</th><th>面试官</th><th>时间</th><th>方式</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(item, i) in filteredList" :key="'l'+i">
            <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)" @click="openCandidateDrawer(item.name)">{{ item.name }}</a></td>
            <td>{{ item.position }}</td><td>{{ item.round }}</td><td>{{ item.interviewer }}</td>
            <td>{{ item.date }} {{ item.time }}</td><td>{{ item.method }}</td>
            <td><StatusBadge :type="STATUS_TYPE_MAP[item.status]">{{ item.statusLabel }}</StatusBadge></td>
            <td style="white-space:nowrap" v-html="renderActions(item)"></td>
          </tr>
        </tbody></table>
        <div class="table-count">共 {{ filteredList.length }} 条</div>
      </div>
    </div>

    <!-- 我的待办 panel -->
    <div class="tab-panel" :class="{ active: activeTab === 'mine' }">
      <div class="table-wrap">
        <table><thead><tr><th>候选人</th><th>岗位</th><th>轮次</th><th>时间</th><th>方式</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(item, i) in filteredMine" :key="'m'+i">
            <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)" @click="openCandidateDrawer(item.name)">{{ item.name }}</a></td>
            <td>{{ item.position }}</td><td>{{ item.round }}</td>
            <td>{{ item.date }} {{ item.time }}</td><td>{{ item.method }}</td>
            <td><StatusBadge :type="STATUS_TYPE_MAP[item.status]">{{ item.statusLabel }}</StatusBadge></td>
            <td style="white-space:nowrap" v-html="renderActions(item)"></td>
          </tr>
        </tbody></table>
        <div class="table-count">共 {{ filteredMine.length }} 条</div>
      </div>
    </div>

    <!-- Calendar Modal -->
    <Teleport to="body">
      <div id="calendarViewModal" class="modal-overlay" v-if="showCalendar" @click.self="showCalendar = false" style="display:flex">
        <div class="modal-box" style="width:720px;max-height:85vh;overflow-y:auto">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
            <h3 style="margin:0">面试日程 · {{ calendarMonthLabel }} 第{{ calendarWeekNum }}周</h3>
            <div style="display:flex;gap:6px">
              <button class="btn btn-outline btn-sm" @click="doAlert('切换月份（demo）')">本月</button>
              <button class="btn btn-ghost btn-sm" @click="showCalendar = false">关闭</button>
            </div>
          </div>
          <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:8px;margin-bottom:16px">
            <div v-for="wd in calendarDays" :key="wd.key"
              :style="{ textAlign:'center', padding:'10px 4px', background: wd.today ? 'var(--c-primary-subtle)' : 'var(--c-bg)', border: wd.today ? '2px solid var(--c-primary)' : '1px solid var(--c-border)', borderRadius:'8px' }">
              <div style="font-size:11px;color:var(--c-sub)">周{{ wd.day }}</div>
              <div style="font-size:20px;font-weight:700;color:var(--c-text)">{{ wd.dateStr }}</div>
              <div v-if="wd.count > 0" style="margin-top:4px;display:inline-block;padding:1px 8px;border-radius:10px;background:var(--c-primary);color:#fff;font-size:11px;font-weight:700">{{ wd.count }}场</div>
              <div v-else style="margin-top:4px;font-size:11px;color:var(--c-sub)">—</div>
            </div>
          </div>
          <div v-for="wd in calendarDays.filter(d => d.count > 0)" :key="'cal-'+wd.key" style="margin-bottom:12px">
            <b style="font-size:13px">{{ wd.monthLabel }} · {{ wd.count }}场</b>
            <table style="margin-top:4px;font-size:12px"><thead><tr><th>候选人</th><th>岗位</th><th>轮次</th><th>时间</th><th>方式</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="(item, i) in wd.items" :key="i">
                <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)">{{ item.name }}</a></td>
                <td>{{ item.position }}</td><td>{{ item.round }}</td><td>{{ item.time }}</td><td>{{ item.method }}</td>
                <td><StatusBadge :type="STATUS_TYPE_MAP[item.status]">{{ item.statusLabel }}</StatusBadge></td>
              </tr>
            </tbody></table>
          </div>
          <div class="modal-actions" style="margin-top:12px">
            <button class="btn btn-ghost btn-sm" @click="showCalendar = false">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import { ALL_INTERVIEWS, STATUSES, STATUS_LABELS, STATUS_TYPE_MAP, ALERTS } from '../data/interview.js';
import { fetchInterviews, fetchInterviewAlerts, createInterview, evaluateInterview } from '../api/interview.js';

const showAlerts = ref(false);
const showCalendar = ref(false);
const currentScope = ref('all');
const activeTab = ref('list');
const listStatus = ref('all');
const mineStatus = ref('all');
const user = localStorage.getItem('hr_user') || '张HR';
const role = localStorage.getItem('hr_role') || 'hr';
const isInterviewerRole = role === 'interviewer' || role === 'temp_interviewer';

const apiInterviewData = ref(null);
const apiAlertData = ref(null);

const INTERVIEWS_SOURCE = computed(() => apiInterviewData.value ?? ALL_INTERVIEWS);
const ALERTS_SOURCE = computed(() => apiAlertData.value ?? ALERTS);

const visibleTabs = computed(() => {
  const tabs = [];
  if (!isInterviewerRole) tabs.push({ id: 'list', label: '全部面试' });
  tabs.push({ id: 'mine', label: '我的待办' });
  return tabs;
});

if (isInterviewerRole) activeTab.value = 'mine';

function countBy(st, mineOnly = false) {
  const pool = mineOnly ? INTERVIEWS_SOURCE.value.filter(i => i.isMine) : INTERVIEWS_SOURCE.value;
  if (st === 'all') return pool.length;
  return pool.filter(i => i.status === st).length;
}
function countMineBy(st) {
  if (st === 'all') return INTERVIEWS_SOURCE.value.filter(i => i.isMine).length;
  return INTERVIEWS_SOURCE.value.filter(i => i.isMine && i.status === st).length;
}

const kpis = computed(() => {
  const source = INTERVIEWS_SOURCE.value;
  const count = (st) => source.filter(i => i.status === st).length;
  return [
    { key:'pending', value: count('pending'), label:'待安排', icon:'' },
    { key:'scheduled', value: count('scheduled'), label:'待面试', icon:'' },
    { key:'evaluating', value: count('evaluating'), label:'待评价', icon:'' },
    { key:'offer', value: count('offer'), label:'待录用', icon:'' },
    { key:'onboard', value: count('onboard'), label:'待入职', icon:'' },
    { key:'done', value: count('done'), label:'已完成', icon:'' },
  ];
});

// 各阶段状态点配色（柔和克制，与企业后台蓝色系协调）
const STAGE_COLORS = {
  pending: '#F59E0B',
  scheduled: '#4F6EF7',
  evaluating: '#8B5CF6',
  offer: '#6366F1',
  onboard: '#14B8A6',
  done: '#22A06B',
};
function stageColor(key) { return STAGE_COLORS[key] || '#4F6EF7'; }

const filteredList = computed(() => {
  return INTERVIEWS_SOURCE.value.filter(item => {
    if (currentScope.value === 'created' && item.createdBy !== user) return false;
    if (listStatus.value !== 'all' && item.status !== listStatus.value) return false;
    return true;
  });
});

const filteredMine = computed(() => {
  return INTERVIEWS_SOURCE.value.filter(item => {
    if (!item.isMine) return false;
    if (mineStatus.value !== 'all' && item.status !== mineStatus.value) return false;
    return true;
  });
});

function renderActions(item) {
  const resumeBtn = '<button class="btn btn-outline btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:open-drawer\',{detail:\'' + item.name + '\'}))">简历</button>';
  switch (item.status) {
    case 'pending':
      return resumeBtn + ' <button class="btn btn-primary btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:schedule\',{detail:\'' + item.name + '|' + item.position + '\'}))">发起面试</button>';
    case 'scheduled':
      return resumeBtn + ' <button class="btn btn-text-danger btn-sm" onclick="window.alert(\'取消面试：' + item.name + '\')">取消</button>';
    case 'evaluating':
      return resumeBtn + ' <button class="btn btn-primary btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:evaluate\',{detail:\'' + item.name + '\'}))">填评价</button>';
    case 'offer':
      return resumeBtn + ' <button class="btn btn-outline btn-sm" onclick="window.alert(\'审批进度：\\n✓ 部门负责人 已通过\\n✓ HR 已通过\\n○ 财务总监 待审批\')">审批中</button> <button class="btn btn-success btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:offer\',{detail:\'' + item.name + '\'}))">发Offer</button>';
    case 'onboard':
      return resumeBtn + ' <span style="font-size:11px;color:var(--c-sub)">待入职 · 08-01</span>';
    default:
      const extra = item.result === 'reject' ? '已回流人才库' : '已入职';
      return resumeBtn + ' <span style="font-size:11px;color:var(--c-sub)">' + extra + '</span>';
  }
}

function onScopeChange() {}
function openCandidateDrawer(name) {
  window.alert('候选人简历抽屉：' + name + '（demo）');
}
async function openGlobalScheduleModal(name, position, dept) {
  const title = name || '选择候选人';
  try {
    const res = await createInterview({ candidate: name || '', position: position || '', dept: dept || '' });
    const id = res?.id || '[sample]';
    window.alert('已创建面试：' + title + ' (ID: ' + id + ')');
  } catch (e) {
    console.warn('[RecruitInterview] createInterview failed, using mock:', e);
    window.alert('新建面试弹窗（demo）：' + title);
  }
}
function doAlert(msg) {
  showAlerts.value = false;
  window.alert(msg);
}

function onDocClick(e) {
  const btn = document.getElementById('alertBtn');
  const dd = document.getElementById('alertDropdown');
  if (showAlerts.value && dd && btn && !btn.contains(e.target) && !dd.contains(e.target)) {
    showAlerts.value = false;
  }
}

onMounted(() => {
  document.addEventListener('click', onDocClick);
  window.addEventListener('interview:evaluate', async (e) => {
    const name = e.detail;
    try {
      await evaluateInterview(name, { result: 'pass', comment: '' });
      window.alert('【面试评价】' + name + '\n已提交评价（通过→待录用）');
    } catch (err) {
      console.warn('[RecruitInterview] evaluateInterview failed, using mock:', err);
      window.alert('【面试评价】\n填写对' + name + '的评价\n[通过→待录用] [不通过→回流]');
    }
  });
  loadFromApi();
});
onUnmounted(() => document.removeEventListener('click', onDocClick));

async function loadFromApi() {
  try {
    const [listRes, alertRes] = await Promise.all([
      fetchInterviews({ tab: activeTab.value }),
      fetchInterviewAlerts()
    ]);
    if (listRes) apiInterviewData.value = listRes.data ?? listRes ?? null;
    if (alertRes) apiAlertData.value = alertRes ?? null;
  } catch (e) { console.warn('[Interview] API fallback:', e.message); }
}

const calendarDays = computed(() => {
  const now = new Date();
  const weekStart = new Date(now);
  weekStart.setDate(now.getDate() - ((now.getDay() + 6) % 7));
  const days = ['一','二','三','四','五','六','日'];
  const todayKey = now.getFullYear() + '-' + String(now.getMonth()+1).padStart(2,'0') + '-' + String(now.getDate()).padStart(2,'0');
  const calData = {};
  INTERVIEWS_SOURCE.value.forEach(item => {
    if (['scheduled','evaluating','onboard','done'].includes(item.status) && item.date !== '待定') {
      const k = '2026-' + item.date;
      if (!calData[k]) calData[k] = [];
      calData[k].push(item);
    }
  });
  const result = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(weekStart);
    d.setDate(weekStart.getDate() + i);
    const key = d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
    const items = calData[key] || [];
    result.push({
      key, items, count: items.length, today: key === todayKey,
      day: days[i], dateStr: String(d.getDate()).padStart(2,'0'),
      monthLabel: (d.getMonth()+1) + '/' + String(d.getDate()).padStart(2,'0') + ' 周' + days[i] + ' · ' + items.length + '场'
    });
  }
  return result;
});

const calendarMonthLabel = computed(() => {
  const now = new Date();
  return (now.getMonth() + 1) + '月';
});
const calendarWeekNum = computed(() => {
  const now = new Date();
  const weekStart = new Date(now);
  weekStart.setDate(now.getDate() - ((now.getDay() + 6) % 7));
  return Math.ceil(weekStart.getDate() / 7);
});
</script>

<style scoped>
.alert-badge {
  position: absolute; top: -4px; right: -4px; width: 16px; height: 16px;
  border-radius: 50%; background: var(--c-reject); color: #fff;
  font-size: 10px; line-height: 16px; text-align: center; font-weight: 700;
}
.alert-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
.alert-dot.reject { background: var(--c-reject); }
.alert-dot.warn { background: var(--c-warn); }
.alert-dot.done { background: var(--c-done); }
[data-slot="iv-pipeline-row"] {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
[data-slot="iv-pipeline-chip"] {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 14px;
  border-radius: 18px;
  border: 1px solid var(--c-border);
  background: var(--c-card);
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
  font-size: 12px;
  color: var(--c-body);
}
[data-slot="iv-pipeline-chip"]:hover {
  border-color: var(--c-primary);
  color: var(--c-primary);
  box-shadow: 0 2px 8px rgba(79, 110, 247, 0.12);
}
/* 仅当真正激活（data-active="true"）时才填充主色，
   注意必须用属性值选择器，否则 data-active="false" 也会被命中 */
[data-slot="iv-pipeline-chip"][data-active="true"] {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
  font-weight: 600;
}
[data-slot="iv-pipeline-chip"][data-active="true"] .iv-dot {
  background: #fff !important;
}
.iv-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
[data-slot="iv-pipeline-val"] {
  font-weight: 700;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
[data-slot="iv-pipeline-label"] {
  opacity: 0.85;
}
</style>
