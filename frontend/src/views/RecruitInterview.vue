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

    <!-- Pipeline status stat-cards -->
    <div class="iv-stat-row">
      <article v-for="kpi in kpis" :key="kpi.key"
        class="hero-summary-card iv-stat-card"
        :class="{ 'is-active': listStatus === kpi.key }"
        role="button" tabindex="0"
        :aria-label="kpi.label + '，' + kpi.value + ' 项，点击筛选'"
        @click="toggleStatus(kpi.key)"
        @keydown.enter.space.prevent="toggleStatus(kpi.key)">
        <span>{{ kpi.label }}</span>
        <strong>{{ kpi.value }}</strong>
        <em>{{ stageHint(kpi.key) }}</em>
        <i class="iv-stat-icon" v-html="stageIcon(kpi.key)"></i>
      </article>
    </div>
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
        <table v-if="filteredList.length > 0"><thead><tr><th>候选人</th><th>岗位</th><th>轮次</th><th>面试官</th><th>时间</th><th>方式</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(item, i) in filteredList" :key="'l'+i">
            <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)" @click="openCandidateDrawer(item.name)">{{ item.name }}</a></td>
            <td>{{ item.position }}</td><td>{{ item.round }}</td><td>{{ item.interviewer }}</td>
            <td>{{ item.date }} {{ item.time }}</td>
            <td>
              <a v-if="item.meetingUrl" :href="item.meetingUrl" target="_blank" rel="noopener" class="meeting-link">
                {{ item.method }}
                <svg viewBox="0 0 24 24" class="meeting-link-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
              </a>
              <template v-else>{{ item.method }}</template>
            </td>
            <td><StatusBadge :type="STATUS_TYPE_MAP[item.status]">{{ item.statusLabel }}</StatusBadge>
              <div v-if="item.candidateConfirm === 'accept'" style="font-size:11px;color:#22a06b;margin-top:2px">✔ 候选人已确认</div>
              <div v-else-if="item.candidateConfirm === 'reject'" style="font-size:11px;color:var(--c-reject);margin-top:2px">✘ 候选人已婉拒</div>
              <div v-else-if="item.emailSent" style="font-size:11px;color:var(--c-sub);margin-top:2px">⏳ 待候选人确认</div>
            </td>
            <td style="white-space:nowrap" v-html="renderActions(item)"></td>
          </tr>
        </tbody></table>
        <EmptyState
          v-else
          title="暂无面试记录"
          description="当前没有符合条件的面试安排，可新建面试"
          action-label="+ 新建面试"
          @action="openGlobalScheduleModal('','','')"
        />
        <div class="table-count">共 {{ filteredList.length }} 条</div>
      </div>
    </div>

    <!-- 我的待办 panel -->
    <div class="tab-panel" :class="{ active: activeTab === 'mine' }">
      <div class="table-wrap">
        <table v-if="filteredMine.length > 0"><thead><tr><th>候选人</th><th>岗位</th><th>轮次</th><th>时间</th><th>方式</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(item, i) in filteredMine" :key="'m'+i">
            <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)" @click="openCandidateDrawer(item.name)">{{ item.name }}</a></td>
            <td>{{ item.position }}</td><td>{{ item.round }}</td>
            <td>{{ item.date }} {{ item.time }}</td>
            <td>
              <a v-if="item.meetingUrl" :href="item.meetingUrl" target="_blank" rel="noopener" class="meeting-link">
                {{ item.method }}
                <svg viewBox="0 0 24 24" class="meeting-link-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
              </a>
              <template v-else>{{ item.method }}</template>
            </td>
            <td><StatusBadge :type="STATUS_TYPE_MAP[item.status]">{{ item.statusLabel }}</StatusBadge>
              <div v-if="item.candidateConfirm === 'accept'" style="font-size:11px;color:#22a06b;margin-top:2px">✔ 候选人已确认</div>
              <div v-else-if="item.candidateConfirm === 'reject'" style="font-size:11px;color:var(--c-reject);margin-top:2px">✘ 候选人已婉拒</div>
              <div v-else-if="item.emailSent" style="font-size:11px;color:var(--c-sub);margin-top:2px">⏳ 待候选人确认</div>
            </td>
            <td style="white-space:nowrap" v-html="renderActions(item)"></td>
          </tr>
        </tbody></table>
        <EmptyState
          v-else
          title="暂无待办事项"
          description="当前没有需要你处理的面试待办"
        />
        <div class="table-count">共 {{ filteredMine.length }} 条</div>
      </div>
    </div>

    <!-- Calendar Modal -->
    <Teleport to="body">
      <div id="calendarViewModal" class="modal-overlay" v-if="showCalendar" @click.self="showCalendar = false" style="display:flex">
        <div class="modal-box" style="width:720px;max-height:85vh;overflow-y:auto">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
            <h3 style="margin:0">面试日程 &middot; {{ calendarMonthLabel }} 第{{ calendarWeekNum }}周</h3>
            <div style="display:flex;gap:6px">
              <button class="btn btn-outline btn-sm" @click="toast.info('切换月份（demo）')">本月</button>
              <button class="btn btn-ghost btn-sm" @click="showCalendar = false">关闭</button>
            </div>
          </div>
          <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:8px;margin-bottom:16px">
            <div v-for="wd in calendarDays" :key="wd.key"
              :style="{ textAlign:'center', padding:'10px 4px', background: wd.today ? 'var(--c-primary-subtle)' : 'var(--c-bg)', border: wd.today ? '2px solid var(--c-primary)' : '1px solid var(--c-border)', borderRadius:'8px' }">
              <div style="font-size:11px;color:var(--c-sub)">周{{ wd.day }}</div>
              <div style="font-size:20px;font-weight:700;color:var(--c-text);font-variant-numeric:tabular-nums">{{ wd.dateStr }}</div>
              <div v-if="wd.count > 0" style="margin-top:4px;display:inline-block;padding:1px 8px;border-radius:10px;background:var(--c-primary);color:#fff;font-size:11px;font-weight:700">{{ wd.count }}场</div>
              <div v-else style="margin-top:4px;font-size:11px;color:var(--c-sub)">&mdash;</div>
            </div>
          </div>
          <div v-if="calendarDaysWithItems.length > 0">
            <div v-for="wd in calendarDaysWithItems" :key="'cal-'+wd.key" style="margin-bottom:12px">
              <b style="font-size:13px">{{ wd.monthLabel }} &middot; {{ wd.count }}场</b>
              <table style="margin-top:4px;font-size:12px"><thead><tr><th>候选人</th><th>岗位</th><th>轮次</th><th>时间</th><th>方式</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="(item, i) in wd.items" :key="i">
                  <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)">{{ item.name }}</a></td>
                  <td>{{ item.position }}</td><td>{{ item.round }}</td><td>{{ item.time }}</td>
                  <td>
                    <a v-if="item.meetingUrl" :href="item.meetingUrl" target="_blank" rel="noopener" class="meeting-link">
                      {{ item.method }}
                      <svg viewBox="0 0 24 24" class="meeting-link-icon" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
                    </a>
                    <template v-else>{{ item.method }}</template>
                  </td>
                  <td><StatusBadge :type="STATUS_TYPE_MAP[item.status]">{{ item.statusLabel }}</StatusBadge>
              <div v-if="item.candidateConfirm === 'accept'" style="font-size:11px;color:#22a06b;margin-top:2px">✔ 候选人已确认</div>
              <div v-else-if="item.candidateConfirm === 'reject'" style="font-size:11px;color:var(--c-reject);margin-top:2px">✘ 候选人已婉拒</div>
              <div v-else-if="item.emailSent" style="font-size:11px;color:var(--c-sub);margin-top:2px">⏳ 待候选人确认</div>
            </td>
                </tr>
              </tbody></table>
            </div>
          </div>
          <div v-else style="text-align:center;padding:24px 0;color:var(--c-sub);font-size:13px">
            本周暂无面试安排
          </div>
          <div class="modal-actions" style="margin-top:12px">
            <button class="btn btn-ghost btn-sm" @click="showCalendar = false">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Schedule Interview Modal -->
    <ScheduleInterviewModal
      :visible="showScheduleModal"
      :candidate="scheduleCandidate"
      :demand="scheduleDemand"
      @close="showScheduleModal = false"
      @success="onScheduleSuccess"
    />

    <!-- Offer Modal -->
    <OfferModal
      :visible="showOfferModal"
      :candidate="offerCandidate"
      :demand="offerDemand"
      :resume-id="offerResumeId"
      :book-id="offerBookId"
      @close="showOfferModal = false"
      @success="onOfferSuccess"
    />

    <!-- 候选人简历抽屉（真实数据） -->
    <CandidateDrawer
      :visible="showCandidateDrawer"
      :candidate-id="activeCandidateId"
      @close="showCandidateDrawer = false"
      @contact="onDrawerAction('contact')"
      @join="onDrawerAction('join')"
    />

    <!-- 面试评价 Modal（强制填写评价理由） -->
    <Teleport to="body">
      <div v-if="showEvalModal" class="modal-overlay" @click.self="showEvalModal = false" style="display:flex">
        <div class="modal-box" style="width:480px">
          <h3 style="margin:0 0 4px">面试评价 · {{ evalTarget.name }}</h3>
          <div style="font-size:12px;color:var(--c-sub);margin-bottom:14px">评价结果与评价理由将同步至招聘流程，评价理由为必填项</div>

          <div style="margin-bottom:12px">
            <div style="font-size:13px;font-weight:600;margin-bottom:6px">评价结果</div>
            <div style="display:flex;gap:8px">
              <button v-for="opt in [{v:'pass',t:'✅ 通过'},{v:'fail',t:'❌ 不通过'},{v:'hold',t:'⏸ 暂缓'}]"
                :key="opt.v" class="btn btn-sm"
                :class="evalForm.result === opt.v ? 'btn-primary' : 'btn-outline'"
                @click="evalForm.result = opt.v">{{ opt.t }}</button>
            </div>
          </div>

          <div style="margin-bottom:12px">
            <div style="font-size:13px;font-weight:600;margin-bottom:6px">综合评分（{{ evalForm.score }} 分）</div>
            <input type="range" min="0" max="100" step="5" v-model.number="evalForm.score" style="width:100%">
          </div>

          <div style="margin-bottom:16px">
            <div style="font-size:13px;font-weight:600;margin-bottom:6px">评价理由 <span style="color:var(--c-reject)">*必填</span></div>
            <textarea v-model="evalForm.comment" rows="4" placeholder="请填写具体评价：技术能力、沟通表现、匹配度等（不少于 5 个字）"
              style="width:100%;padding:8px 10px;border:1px solid var(--c-border);border-radius:8px;font-size:13px;font-family:inherit;resize:vertical"></textarea>
          </div>

          <div class="modal-actions" style="display:flex;justify-content:flex-end;gap:8px">
            <button class="btn btn-ghost btn-sm" @click="showEvalModal = false">取消</button>
            <button class="btn btn-primary btn-sm" :disabled="evalSaving" @click="submitEvaluation">
              {{ evalSaving ? '提交中...' : '提交评价' }}
            </button>
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
import { fetchInterviews, fetchInterviewAlerts, createInterview, evaluateInterview, completeInterview } from '../api/interview.js';
import { useToast } from '../composables/useToast.js';
import { useAppError } from '../composables/useAppError.js';
import { KPI_ICONS } from '../components/kpiIcons.js';
import ScheduleInterviewModal from '../components/ScheduleInterviewModal.vue';
import OfferModal from '../components/OfferModal.vue';
import EmptyState from '../components/EmptyState.vue';
import CandidateDrawer from '../components/CandidateDrawer.vue';

const { toast } = useToast();
const { handleError } = useAppError();

const showAlerts = ref(false);
const showCalendar = ref(false);
const showScheduleModal = ref(false);
const showOfferModal = ref(false);
const scheduleCandidate = ref({ name: '', id: '' });
const scheduleDemand = ref({ position: '', id: '' });
const offerCandidate = ref({ name: '', id: '' });
const offerDemand = ref({ position: '', id: '' });
const offerResumeId = ref(0);
const offerBookId = ref('');
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

const STAGE_ICONS = {
  pending: KPI_ICONS.clock,
  scheduled: KPI_ICONS.calendar,
  evaluating: KPI_ICONS.edit,
  offer: KPI_ICONS.check,
  onboard: KPI_ICONS.userCheck,
  done: KPI_ICONS.checkSquare,
};
function stageIcon(key) { return STAGE_ICONS[key] || KPI_ICONS.clock; }

const STAGE_HINTS = {
  pending: '待协调时间',
  scheduled: '流程进行中',
  evaluating: '反馈待回收',
  offer: 'Offer 审批中',
  onboard: '入职跟进中',
  done: '本期已闭环',
};
function stageHint(key) { return STAGE_HINTS[key] || ''; }

function toggleStatus(key) { listStatus.value = listStatus.value === key ? 'all' : key; }

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
      return resumeBtn + ' <button class="btn btn-primary btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:complete\',{detail:\'' + item.id + '|' + item.name + '\'}))">完成面试</button> <button class="btn btn-text-danger btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:cancel\',{detail:\'' + item.name + '\'}))">取消</button>';
    case 'evaluating':
      return resumeBtn + ' <button class="btn btn-primary btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:evaluate\',{detail:\'' + item.id + '|' + item.name + '\'}))">填评价</button>';
    case 'offer':
      if (item.offerStatus === 1) {
        return resumeBtn + ' <span style="font-size:11px;color:var(--c-sub)">Offer已发送（' + (item.offerNo || '') + '），等待候选人确认</span>';
      }
      return resumeBtn + ' <button class="btn btn-outline btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:approval\',{detail:\'' + item.name + '\'}))">审批中</button> <button class="btn btn-success btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:offer\',{detail:\'' + item.name + '\'}))">发Offer</button>';
    case 'onboard':
      return resumeBtn + ' <span style="font-size:11px;color:#22a06b">已录用，待入职</span>';
    default:
      if (item.offerStatus === 3) {
        return resumeBtn + ' <span style="font-size:11px;color:var(--c-reject)">候选人已拒绝 Offer</span>';
      }
      const extra = item.result === 'reject' ? '已淘汰 · 回流人才库' : '已入职';
      return resumeBtn + ' <span style="font-size:11px;color:var(--c-sub)">' + extra + '</span>';
  }
}

function onScopeChange() {}

const showCandidateDrawer = ref(false);
const activeCandidateId = ref('');

async function openCandidateDrawer(name) {
  // 从当前列表数据中找到该面试记录，取后端下发的真实候选人编号
  const item = INTERVIEWS_SOURCE.value.find(i => i.name === name);
  const candidateId = item && item.candidateId ? item.candidateId : '';
  if (!candidateId) {
    toast.warning(`${name} 未关联真实候选人档案（该记录可能是旧测试数据）`);
    return;
  }
  activeCandidateId.value = candidateId;
  showCandidateDrawer.value = true;
}

function onDrawerAction(kind) {
  showCandidateDrawer.value = false;
  toast.info(kind === 'contact' ? '请前往「人才库」页面联系该候选人' : '请前往「人才库」页面将候选人加入需求');
}

function openGlobalScheduleModal(name, position, dept) {
  scheduleCandidate.value = { name: name || '', id: '' };
  scheduleDemand.value = { position: position || '', id: dept || '' };
  showScheduleModal.value = true;
}

async function doAlert(msg) {
  showAlerts.value = false;
  try {
    if (msg === '发起Offer' || msg === '发起调岗') {
      const targetName = msg === '发起Offer' ? '郑一' : '王工';
      try {
        await createInterview({ name: targetName, position: '待定', type: msg === '发起Offer' ? 'offer' : 'transfer' });
        const actionLabel = msg === '发起Offer' ? 'Offer' : '调岗';
        toast.success(actionLabel + '已发起：' + targetName + '，系统已发送飞书通知');
      } catch (e) {
        console.warn('[RecruitInterview] ' + msg + ' API failed:', e);
        toast.info(msg + ' ' + targetName + '（DEMO）');
      }
    } else if (msg.indexOf('填写对') === 0) {
      const name = msg.replace('填写对', '').replace('的评价', '');
      const item = INTERVIEWS_SOURCE.value.find(i => i.name === name && i.status === 'evaluating');
      if (item) {
        openEvalModal(item.id, item.name);
      } else {
        toast.warning('未找到 ' + name + ' 的待评价面试，请先在列表中点击"完成面试"');
      }
    } else {
      toast.info(msg);
    }
  } catch (e) {
    console.warn('[RecruitInterview] doAlert failed:', e);
    toast.info(msg);
  }
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
  window.addEventListener('interview:evaluate', handleEvaluate);
  window.addEventListener('interview:complete', handleComplete);
  window.addEventListener('interview:schedule', handleSchedule);
  window.addEventListener('interview:offer', handleOffer);
  window.addEventListener('interview:cancel', handleCancel);
  window.addEventListener('interview:approval', handleApproval);
  window.addEventListener('interview:open-drawer', handleOpenDrawer);
  loadFromApi();
});

onUnmounted(() => {
  document.removeEventListener('click', onDocClick);
  window.removeEventListener('interview:evaluate', handleEvaluate);
  window.removeEventListener('interview:complete', handleComplete);
  window.removeEventListener('interview:schedule', handleSchedule);
  window.removeEventListener('interview:offer', handleOffer);
  window.removeEventListener('interview:cancel', handleCancel);
  window.removeEventListener('interview:approval', handleApproval);
  window.removeEventListener('interview:open-drawer', handleOpenDrawer);
});

// ── 面试评价（强制填写评价理由）──
const showEvalModal = ref(false);
const evalTarget = ref({ id: '', name: '' });
const evalForm = reactive({ result: 'pass', score: 75, comment: '' });
const evalSaving = ref(false);

function openEvalModal(id, name) {
  evalTarget.value = { id, name };
  Object.assign(evalForm, { result: 'pass', score: 75, comment: '' });
  showEvalModal.value = true;
}

async function submitEvaluation() {
  if (!evalForm.comment || evalForm.comment.trim().length < 5) {
    toast.warning('请填写评价理由（不少于 5 个字）');
    return;
  }
  evalSaving.value = true;
  try {
    if (!evalTarget.value.id) {
      toast.error('该记录未同步到服务端（可能为本地演示数据），无法提交评价');
      return;
    }
    const r = await evaluateInterview(evalTarget.value.id, {
      result: evalForm.result,
      score: evalForm.score,
      comment: evalForm.comment.trim(),
    });
    const label = { pass: '通过，进入待录用', fail: '不通过，已回流人才库', hold: '暂缓，保持待评价' }[evalForm.result];
    toast.success(`【面试评价】${evalTarget.value.name}：${label}`);
    showEvalModal.value = false;
    await loadFromApi();
  } catch (err) {
    handleError(err, 'RecruitInterview.submitEvaluation');
    toast.error('评价提交失败：' + (err?.response?.data?.message || err.message || '未知错误'));
  } finally {
    evalSaving.value = false;
  }
}

async function handleEvaluate(e) {
  const parts = String(e.detail).split('|');
  // 新格式: 'INT0001|张三'；旧格式仅姓名时按姓名查找
  if (parts.length === 2) {
    openEvalModal(parts[0], parts[1]);
    return;
  }
  const item = INTERVIEWS_SOURCE.value.find(i => i.name === parts[0] && i.status === 'evaluating');
  if (item) {
    openEvalModal(item.id, item.name);
  } else {
    toast.warning('未找到 ' + parts[0] + ' 的待评价面试');
  }
}

async function handleComplete(e) {
  const parts = String(e.detail).split('|');
  const id = parts[0], name = parts[1] || '';
  if (!id || id === 'undefined') {
    toast.error('该记录未同步到服务端（可能为本地演示数据），无法操作');
    return;
  }
  if (!window.confirm('确认 ' + name + ' 的面试已完成？完成后将进入待评价状态')) return;
  try {
    await completeInterview(id, { is_arrive: 1 });
    toast.success('【面试完成】' + name + ' 已进入待评价，请面试官提交评价');
    await loadFromApi();
  } catch (err) {
    handleError(err, 'RecruitInterview.handleComplete');
    toast.error('操作失败：' + (err?.response?.data?.message || err.message || '未知错误'));
  }
}

async function handleSchedule(e) {
  const parts = e.detail.split('|');
  const name = parts[0] || '';
  const position = parts[1] || '';
  scheduleCandidate.value = { name, id: '' };
  scheduleDemand.value = { position, id: '' };
  showScheduleModal.value = true;
}

async function handleCancel(e) {
  const name = e.detail;
  const item = INTERVIEWS_SOURCE.value.find(i => i.name === name);
  if (!item || !item.id) {
    toast.error('该记录未同步到服务端（可能为本地演示数据），无法取消');
    return;
  }
  if (!window.confirm('确认取消 ' + name + ' 的面试？')) return;
  try {
    const { cancelInterview } = await import('../api/interview.js');
    await cancelInterview(item.id, 'HR 手动取消');
    toast.success('已取消 ' + name + ' 的面试');
    await loadFromApi();
  } catch (err) {
    console.warn('[RecruitInterview] cancel failed:', err);
    toast.error('取消失败：' + (err.message || '未知错误'));
  }
}

function handleApproval(e) {
  const name = e.detail;
  toast.info('审批进度：请进入需求管理页面查看审批详情');
}

function handleOffer(e) {
  const name = e.detail;
  const item = INTERVIEWS_SOURCE.value.find(i => i.name === name) || {};
  offerCandidate.value = { name, id: item.candidateId || '' };
  offerDemand.value = { position: item.position || '', id: item.demandId || 0 };
  offerResumeId.value = item.resumeId || 0;
  offerBookId.value = item.id || '';
  showOfferModal.value = true;
}

function handleOpenDrawer(e) {
  const name = e.detail;
  openCandidateDrawer(name);
}

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

const calendarDaysWithItems = computed(() => calendarDays.value.filter(d => d.count > 0));

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

function onScheduleSuccess(result) {
  const msg = result?.rounds
    ? '已安排 ' + result.rounds + ' 轮面试，面试ID: ' + (result.results?.map(r => r.id).join(', ') || '')
    : '面试安排成功';
  toast.success(msg + '，系统已发送飞书通知给面试官');
  loadFromApi();
}

function onOfferSuccess(result) {
  const base = '已发送Offer给 ' + (result?.name || '候选人') + '，Offer编号: ' + (result?.id || '');
  if (result?.emailSent) {
    toast.success(base + '，确认邮件已发送至候选人邮箱');
  } else {
    toast.success(base + (result?.emailMsg ? '（邮件未发送：' + result.emailMsg + '）' : ''));
  }
  loadFromApi();
}
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
.iv-stat-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.iv-stat-card {
  cursor: pointer;
  transition: border-color .15s, box-shadow .15s;
}
.iv-stat-card:hover { border-color: var(--c-primary); }
.iv-stat-card.is-active {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px var(--c-primary-subtle);
}
.iv-stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-primary);
}
.iv-stat-icon::after { display: none; }
.iv-stat-icon svg { width: 18px; height: 18px; }

.meeting-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--c-primary);
  font-weight: 600;
  text-decoration: none;
}
.meeting-link:hover { text-decoration: underline; }
.meeting-link-icon { width: 12px; height: 12px; flex-shrink: 0; }

@media (max-width: 1200px) { .iv-stat-row { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 720px) { .iv-stat-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
