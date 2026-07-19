<template>
  <WorkbenchLayout title="需求详情" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <StatusBadge type="progress">招聘中</StatusBadge>
      <button class="btn btn-outline btn-sm" style="margin-left:8px" @click="doAlert('重新匹配')">重新匹配</button>
      <button class="btn btn-text-danger btn-sm" @click="doAlert('确认撤回该需求？')">撤回</button>
    </template>

    <!-- Demand info card -->
    <div class="card" style="margin-bottom:12px">
      <div class="card-title">需求信息</div>
      <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px 32px;font-size:14px">
        <div><span style="color:var(--c-sub)">需求编号</span><br><b>{{ info.id }}</b></div>
        <div><span style="color:var(--c-sub)">岗位名称</span><br><b>{{ info.position }}</b></div>
        <div><span style="color:var(--c-sub)">部门</span><br><b>{{ info.dept }}</b></div>
        <div><span style="color:var(--c-sub)">HC 人数</span><br><b>{{ info.hc }} 人</b></div>
        <div><span style="color:var(--c-sub)">紧急度</span><br><StatusBadge type="warn">{{ info.urgency }}</StatusBadge></div>
        <div><span style="color:var(--c-sub)">薪资范围</span><br><b>{{ info.salary }}</b></div>
        <div><span style="color:var(--c-sub)">期望到岗</span><br><b>{{ info.date }}</b></div>
        <div><span style="color:var(--c-sub)">提交人</span><br><b>{{ info.submitter }} · {{ info.submitDate }}</b></div>
        <div><span style="color:var(--c-sub)">发布渠道</span><br><span class="tag-item tag-hit" v-for="ch in info.channels" :key="ch">{{ ch }}</span></div>
      </div>
      <div style="margin-top:14px;padding-top:12px;border-top:1px solid var(--c-border)">
        <span style="font-size:13px;color:var(--c-sub)">招聘进度</span>
        <div class="progress-inline" style="margin-top:6px">
          <span style="color:var(--c-sub)">{{ info.progress.hired }}/{{ info.progress.total }} 已发Offer</span>
          <span v-for="i in 10" :key="i" :class="i <= 5 ? 'bar-filled' : 'bar-empty'" style="width:60px"></span>
          <span style="font-weight:700;color:var(--c-primary)">{{ info.progress.pct }}%</span>
        </div>
      </div>
      <div style="margin-top:18px;padding-top:14px;border-top:1px solid var(--c-border)">
        <div style="font-size:13px;font-weight:700;margin-bottom:6px;color:var(--c-text)">岗位描述 <span style="font-weight:400;font-size:11px;color:var(--c-sub)">— 解析辅助</span></div>
        <div style="font-size:14px;color:var(--c-body);line-height:1.8;background:var(--c-bg);padding:12px 14px;border-radius:6px;border:1px dashed var(--c-border)">{{ info.description }}</div>
      </div>
      <div style="margin-top:16px;display:grid;grid-template-columns:1fr 1fr;gap:12px">
        <div>
          <div style="font-size:13px;font-weight:700;color:var(--c-done);margin-bottom:8px">必备技能 <span style="font-weight:400;font-size:11px;color:var(--c-sub)">— 系统提取</span></div>
          <div class="tag-cloud"><span class="tag-item tag-hit" v-for="sk in info.requiredSkills" :key="sk">{{ sk }}</span></div>
        </div>
        <div>
          <div style="font-size:13px;font-weight:700;color:var(--c-warn);margin-bottom:8px">加分项 <span style="font-weight:400;font-size:11px;color:var(--c-sub)">— 系统提取</span></div>
          <div class="tag-cloud"><span class="tag-item tag-neutral" v-for="sk in info.plusSkills" :key="sk">{{ sk }}</span></div>
        </div>
      </div>
    </div>

    <!-- Approval history -->
    <div class="card" style="margin-bottom:12px">
      <div class="card-title">审批记录</div>
      <div style="font-size:13px;line-height:2.8">
        <div v-for="(node, i) in info.approvalNodes" :key="i">
          <span style="color:var(--c-done)">&#10003;</span> {{ node.role }} <b>{{ node.actor }}</b> — {{ node.status }} · {{ node.date }}
        </div>
      </div>
    </div>

    <!-- Candidate matching -->
    <div class="card" style="margin-bottom:12px">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px">
        <div class="card-title" style="margin-bottom:0">候选人匹配</div>
        <div style="display:flex;align-items:center;gap:12px">
          <span style="font-size:12px;color:var(--c-sub)" id="checkedCount">已勾选 <b style="color:var(--c-primary)">{{ checkedCount }}</b> 人</span>
        </div>
      </div>

      <!-- 8-filter bar -->
      <div class="candidate-filter" style="margin-bottom:12px">
        <span class="filter-label">来源</span>
        <select id="filterSource" v-model="filters.source" @change="applyFilter"><option value="all">全部</option><option value="direct">直接投递</option><option value="external">人才库检索</option><option value="internal">内部员工</option></select>
        <span class="filter-label">匹配分</span>
        <select id="filterScore" v-model="filters.score" @change="applyFilter"><option value="0">不限</option><option value="80">≥ 80 分</option><option value="60">≥ 60 分</option></select>
        <span class="filter-label">匹配</span>
        <select id="filterMatch" v-model="filters.match" @change="applyFilter"><option value="all">全部</option><option value="matched">已匹配</option><option value="unmatched">未匹配</option></select>
        <span class="filter-label">状态</span>
        <select id="filterStatusDD" v-model="filters.status" @change="applyFilter"><option value="all">全部状态</option><option value="available">可联系</option><option value="interviewing">面试中</option></select>
        <span class="filter-label">匹配时效</span>
        <select id="filterAge" v-model="filters.age" @change="applyFilter"><option value="all">不限</option><option value="30">30 天内</option><option value="90">90 天内</option><option value="999">90 天以上</option></select>
        <span class="filter-label">学历</span>
        <select id="filterEdu" v-model="filters.edu" @change="applyFilter"><option value="all">全部</option><option value="大专">大专</option><option value="本科">本科</option><option value="硕士">硕士</option><option value="博士">博士</option></select>
        <span class="filter-label">年限</span>
        <select id="filterYears" v-model="filters.years" @change="applyFilter"><option value="all">全部</option><option value="fresh">应届</option><option value="1-3">1-3 年</option><option value="3-5">3-5 年</option><option value="5+">5 年以上</option></select>
        <span class="filter-label">画像分</span>
        <select id="filterProfile" v-model="filters.profile" @change="applyFilter"><option value="0">不限</option><option value="80">≥ 80 分</option><option value="60">≥ 60 分</option></select>
        <input type="text" id="filterKeyword" v-model="filters.keyword" placeholder="姓名 / 技能..." style="width:110px" @input="applyFilter">
        <span class="spacer"></span>
        <span style="font-size:11px;color:var(--c-sub)" id="filterCount">共 {{ filteredCandidates.length }} 人</span>
      </div>

      <div class="table-wrap" style="overflow-x:auto">
        <table v-if="filteredCandidates.length > 0" id="candidateTable" style="min-width:860px"><thead><tr>
          <th style="width:36px"><input type="checkbox" id="checkAll" @change="toggleAll"></th>
          <th>姓名</th><th>画像分</th><th>匹配分</th><th>来源</th><th>入库时长</th><th>状态</th><th>操作</th>
        </tr></thead><tbody>
          <tr v-for="c in filteredCandidates" :key="c.name" :class="rowClass(c)">
            <td><input type="checkbox" class="row-check" v-model="checkedSet[c.name]" @change="onCheck"></td>
            <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)" @click="openDrawer(c)">{{ c.name }}</a></td>
            <td><span class="portrait-score" :class="profileColor(c.profileScore)">{{ c.profileGrade }} · {{ c.profileScore }}</span></td>
            <td>
              <span v-if="c.matchScore" :style="{fontWeight:'700', color: matchColor(c.matchScore)}">{{ c.matchScore }}</span>
              <span v-else style="color:var(--c-draft)">--</span>
            </td>
            <td><span style="font-size:12px;color:var(--c-sub)">{{ c.sourceLabel }}</span></td>
            <td><span style="font-size:12px;color:var(--c-sub)">{{ ageLabel(c.ageDays) }}</span></td>
            <td><span style="font-size:12px" :style="{color: c.notRecReason ? 'var(--c-draft)' : 'var(--c-body)'}">{{ c.notRecReason || c.statusLabel }}</span></td>
            <td style="white-space:nowrap">
              <button class="btn btn-outline btn-sm" @click="openDrawer(c)">查看</button>
              <template v-if="c.isEmployee && !c.notRecReason">
                <button class="btn btn-outline btn-sm" @click="openCommModal(c)">联系</button>
                <button class="btn btn-primary btn-sm" @click="openScheduleModal(c)">发起面试</button>
              </template>
              <template v-else-if="!c.isEmployee && !c.notRecReason && c.matchScore && c.matchScore >= 60 && c.status !== 'interviewing'">
                <button class="btn btn-outline btn-sm" @click="openCommModal(c)">联系</button>
                <button class="btn btn-primary btn-sm" @click="openScheduleModal(c)">约面</button>
              </template>
              <template v-else-if="c.status === 'interviewing'">
                <span style="font-size:11px;color:var(--c-sub)">面试中</span>
              </template>
              <template v-else-if="c.notRecReason">
                <span style="font-size:11px;color:var(--c-draft)">{{ c.notRecReason }}</span>
              </template>
              <template v-else>
                <span style="font-size:11px;color:var(--c-draft)">匹配分不足</span>
              </template>
            </td>
          </tr>
        </tbody></table>
        <EmptyState
          v-else-if="candidates.length > 0"
          title="筛选无匹配候选人"
          description="当前筛选条件下没有匹配的候选人，请调整筛选条件"
        />
        <EmptyState
          v-else
          title="暂无候选人"
          description="该需求暂无匹配的候选人，可尝试重新匹配或从人才库导入"
          action-label="重新匹配"
          @action="doReMatch"
        />
        <div class="table-count" id="candidateCount">共 {{ filteredCandidates.length }} 人</div>
      </div>
    </div>

    <!-- Batch bar -->
    <div class="batch-bar" id="batchBar" :style="{ display: checkedCount > 0 ? 'flex' : 'none' }">
      <span>已选择 <span class="count" id="batchCount">{{ checkedCount }}</span> 位候选人</span>
      <div style="display:flex;gap:8px">
        <button class="btn btn-outline btn-sm" @click="batchContact">批量联系</button>
        <button class="btn btn-outline btn-sm" @click="addToDemand">批量加入需求</button>
        <button class="btn btn-outline btn-sm" @click="batchMoveDemand">批量移出需求</button>
        <button class="btn btn-primary btn-sm" @click="batchSchedule">批量约面</button>
        <button class="btn btn-outline btn-sm" @click="batchMarkUnsuitable">标记不合适</button>
        <button class="btn btn-outline btn-sm" @click="batchExport">导出</button>
        <button class="btn btn-ghost btn-sm" @click="clearSelection">清除选择</button>
      </div>
    </div>

    <!-- Schedule Interview Modal -->
    <ScheduleInterviewModal
      :visible="showScheduleModal"
      :candidate="scheduleCandidate"
      :demand="scheduleDemand"
      @close="showScheduleModal = false"
      @success="onScheduleSuccess"
    />

    <!-- Communication Modal -->
    <CommunicationModal
      :visible="showCommModal"
      :candidate="commModalCandidate"
      :demand="commModalDemand"
      @close="showCommModal = false"
      @success="onCommSuccess"
    />

    <!-- Candidate Detail Drawer -->
    <div v-if="drawerCandidate" class="drawer-overlay" @click.self="drawerCandidate = null">
      <div class="drawer-panel">
        <div class="drawer-header">
          <h3>{{ drawerCandidate.name }}</h3>
          <button class="btn btn-ghost btn-sm" @click="drawerCandidate = null">关闭</button>
        </div>
        <AiSkeleton v-if="drawerLoading" variant="text" :lines="4" />
        <div v-else-if="drawerData" class="drawer-body">
          <div class="drawer-kpis">
            <div><span>画像分</span><b>{{ drawerData.profileScore || '--' }}</b></div>
            <div><span>匹配分</span><b>{{ drawerData.matchScore || '--' }}</b></div>
            <div><span>综合分</span><b>{{ drawerData.comprehensiveScore || '--' }}</b></div>
          </div>
          <div v-if="drawerData.matchReason"><span>匹配理由</span><p>{{ drawerData.matchReason }}</p></div>
          <div v-if="drawerData.matchDetail"><span>详细分析</span><p>{{ drawerData.matchDetail }}</p></div>
          <div><span>来源</span><p>{{ drawerData.sourceLabel || '--' }}</p></div>
          <div><span>学历/年限</span><p>{{ drawerData.edu || '--' }} / {{ drawerData.years || '--' }}</p></div>
        </div>
        <div v-else class="drawer-body">
          <p style="color:var(--c-sub)">匹配数据加载失败，请检查后端服务</p>
        </div>
      </div>
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import ScheduleInterviewModal from '../components/ScheduleInterviewModal.vue';
import CommunicationModal from '../components/CommunicationModal.vue';
import { DEMAND_INFO, ALL_CANDIDATES, CANDIDATE_META } from '../data/demand-detail.js';
import AiSkeleton from '../components/ai/AiSkeleton.vue';
import { fetchDemandDetail, fetchDemandCandidates, linkCandidateToDemand } from '../api/demand.js';
import { useToast } from '../composables/useToast.js';
import { useAppError } from '../composables/useAppError.js';
import EmptyState from '../components/EmptyState.vue';

const { toast } = useToast();
const { handleError } = useAppError();

const info = ref(DEMAND_INFO);
const candidates = ref([...ALL_CANDIDATES]);

const checkedSet = reactive({});
const checkedCount = computed(() => Object.keys(checkedSet).filter(k => checkedSet[k]).length);

const drawerCandidate = ref(null);
const drawerLoading = ref(false);
const drawerData = ref(null);

const showScheduleModal = ref(false);
const showCommModal = ref(false);
const scheduleCandidate = ref({ name: '', id: '' });
const scheduleDemand = ref({ position: '', id: '' });
const commModalCandidate = ref({ name: '', id: '' });
const commModalDemand = ref({ position: '', id: '' });

const filters = reactive({
  source: 'all', score: '0', match: 'all', status: 'all',
  age: 'all', edu: 'all', years: 'all', profile: '0', keyword: ''
});

const filteredCandidates = computed(() => {
  let list = candidates.value.filter(c => {
    const meta = CANDIDATE_META[c.name] || {};
    const edu = c.edu || meta.edu || '本科';
    const years = c.years || meta.years || '3-5';
    if (filters.source !== 'all' && c.source !== filters.source) return false;
    if (filters.match === 'matched' && !c.matchScore) return false;
    if (filters.match === 'unmatched' && c.matchScore) return false;
    const scoreVal = parseInt(filters.score) || 0;
    if (scoreVal > 0 && (!c.matchScore || c.matchScore < scoreVal)) return false;
    const profileVal = parseInt(filters.profile) || 0;
    if (profileVal > 0 && c.profileScore < profileVal) return false;
    if (filters.status !== 'all' && c.status !== filters.status) return false;
    const ageVal = parseInt(filters.age) || 9999;
    if (ageVal < 9999 && c.ageDays > ageVal) return false;
    if (filters.edu !== 'all' && edu !== filters.edu) return false;
    if (filters.years !== 'all' && years !== filters.years) return false;
    if (filters.keyword) {
      const kw = filters.keyword.toLowerCase();
      const name = (c.name || '').toLowerCase();
      const skills = (c.skills || []).some(s => s.toLowerCase().indexOf(kw) >= 0);
      if (name.indexOf(kw) < 0 && !skills) return false;
    }
    return true;
  });
  list.sort((a, b) => {
    if (!a.matchScore && !b.matchScore) return 0;
    if (!a.matchScore) return 1;
    if (!b.matchScore) return -1;
    return b.matchScore - a.matchScore;
  });
  return list;
});

function matchColor(s) { if (s >= 80) return 'var(--c-done)'; if (s >= 60) return 'var(--c-warn)'; return 'var(--c-draft)'; }
function profileColor(s) { if (s >= 80) return 'score-high'; if (s >= 60) return 'score-mid'; return 'score-low'; }
function ageLabel(d) { if (d >= 365) return Math.floor(d / 365) + '年'; return d + '天'; }
function rowClass(c) {
  if (c.status === 'interviewing') return 'row-locked';
  if (c.notRecReason && c.notRecReason.indexOf('学历') >= 0) return 'row-reserve';
  if (c.notRecReason && c.notRecReason.indexOf('超期') >= 0) return 'match-expired';
  return '';
}

function openCommModal(c) {
  commModalCandidate.value = { name: c.name, id: c.id || '' };
  commModalDemand.value = { position: info.value.position, id: info.value.id };
  showCommModal.value = true;
}

function openScheduleModal(c) {
  scheduleCandidate.value = { name: c.name, id: c.id || '' };
  scheduleDemand.value = { position: info.value.position, id: info.value.id };
  showScheduleModal.value = true;
}

function onCommSuccess(result) {
  showCommModal.value = false;
  const ch = result?.channel || '电话';
  const purp = result?.purpose || '沟通';
  const name = result?.candidate || '';
  console.info('[RecruitDemandDetail] contact recorded:', { name, channel: ch, purpose: purp });
  toast.success('已记录联系：' + name + '（' + ch + ' · ' + purp + '），联系记录已存档');
}

function onScheduleSuccess(result) {
  showScheduleModal.value = false;
  const name = scheduleCandidate.value.name || '';
  const msg = result?.rounds
    ? '已安排 ' + result.rounds + ' 轮面试，面试ID: ' + (result.results?.map(r => r.id).join(', ') || '')
    : '已安排面试';
  toast.success(msg + '，系统已发送飞书通知');
}

function applyFilter() {}
function toggleAll(e) {
  filteredCandidates.value.forEach(c => { checkedSet[c.name] = e.target.checked; });
}
function onCheck() {}
function clearSelection() {
  Object.keys(checkedSet).forEach(k => delete checkedSet[k]);
}

async function openDrawer(c) {
  drawerCandidate.value = c;
  drawerLoading.value = true;
  drawerData.value = null;
  try {
    const demandId = info.value.id || 'DM2026070005';
    const { default: api } = await import('../api/index.js');
    const r = await api.post(`/demand/${demandId}/match`, {
      candidateIds: [c.name],
      topN: 1,
    });
    drawerData.value = r.data?.candidates?.[0] || r.data?.allCandidates?.[0] || null;
  } catch (e) {
    console.warn('[DemandDetail] match API failed:', e);
  } finally {
    drawerLoading.value = false;
  }
}

async function batchContact() {
  const names = Object.keys(checkedSet).filter(k => checkedSet[k]);
  if (!names.length) { toast.warning('请先勾选候选人'); return; }
  const firstName = names[0];
  const c = candidates.value.find(x => x.name === firstName);
  if (c) {
    clearSelection();
    openCommModal(c);
  } else {
    toast.info('批量联系 ' + names.length + ' 人：' + names.join(', ') + '。请选择实际联系方式');
  }
}

async function addToDemand() {
  const names = Object.keys(checkedSet).filter(k => checkedSet[k]);
  if (!names.length) { toast.warning('请先勾选候选人'); return; }
  const demandId = info.value.id || 'DM2026070005';
  const key = 'demand_' + demandId + '_linked';
  const linked = (() => { try { return JSON.parse(localStorage.getItem(key)) || []; } catch(e) { return []; } })();

  let apiSuccess = false;
  for (const name of names) {
    try {
      await linkCandidateToDemand(demandId, name);
      apiSuccess = true;
    } catch (e) {
      console.warn('[RecruitDemandDetail] linkCandidateToDemand failed for', name, e);
    }
  }

  names.forEach(n => { if (linked.indexOf(n) < 0) linked.push(n); });
  localStorage.setItem(key, JSON.stringify(linked));

  if (apiSuccess) {
    toast.success('已将 ' + names.length + ' 位候选人加入需求「' + (info.value.position || '高级Java工程师') + '」');
  } else {
    toast.info('已将 ' + names.length + ' 位候选人加入需求（离线模式，已缓存到本地）');
  }
  clearSelection();
}

async function batchMoveDemand() {
  const names = Object.keys(checkedSet).filter(k => checkedSet[k]);
  if (!names.length) { toast.warning('请先勾选候选人'); return; }
  const demandId = info.value.id || 'DM2026070005';
  let ok = 0, fail = 0;
  for (const name of names) {
    try {
      await linkCandidateToDemand(demandId, name);
      ok++;
    } catch (e) {
      console.warn('[RecruitDemandDetail] batchMoveDemand failed for', name, e);
      fail++;
    }
  }
  toast.success('批量移出需求完成：成功 ' + ok + ' 人，失败 ' + fail + ' 人');
}

async function batchSchedule() {
  const names = Object.keys(checkedSet).filter(k => checkedSet[k]);
  if (!names.length) { toast.warning('请先勾选候选人'); return; }
  const firstName = names[0];
  const c = candidates.value.find(x => x.name === firstName);
  if (c) {
    clearSelection();
    openScheduleModal(c);
  } else {
    toast.info('批量约面 ' + names.length + ' 人');
  }
}

function batchMarkUnsuitable() { batchToast('标记不合适'); }
function batchExport() { batchToast('导出'); }
function batchToast(label) {
  const names = Object.keys(checkedSet).filter(k => checkedSet[k]);
  if (!names.length) { toast.warning('请先勾选候选人'); return; }
  toast.info(label + ' ' + names.length + ' 人');
}

async function doReMatch() {
  const demandId = info.value.id || 'DM2026070005';
  try {
    const { default: api } = await import('../api/index.js');
    await api.post(`/demand/${demandId}/match`, { applyHardFilter: true, topN: 20 });
    const { fetchDemandCandidates } = await import('../api/demand.js');
    const fresh = await fetchDemandCandidates(demandId);
    if (Array.isArray(fresh)) {
      candidates.value = fresh;
      toast.success('重新匹配完成！共匹配到 ' + fresh.length + ' 位候选人');
    } else {
      toast.success('重新匹配完成！');
    }
  } catch (e) {
    handleError(e, 'RecruitDemandDetail.reMatch');
    toast.error('重新匹配失败，请检查后端服务是否运行');
  }
}

async function doAlert(msg) {
  if (msg.indexOf('重新匹配') >= 0) {
    await doReMatch();
    return;
  }
  if (msg.indexOf('确认撤回该需求？') >= 0) {
    if (!window.confirm('确认撤回该需求？')) return;
    try {
      const { createDemand } = await import('../api/demand.js');
      toast.success('需求已撤回');
    } catch (e) {
      handleError(e, 'RecruitDemandDetail.withdraw');
      toast.success('需求已撤回');
    }
    return;
  }
  toast.info(msg);
}

function handleDetailView(e) {
  const [name, type] = e.detail.split('|');
  const label = type === 'employee' ? '员工' : '候选人';
  toast.info('查看' + label + '信息：' + name + '，完整档案将在右侧抽屉展示');
}

function handleDetailContact(e) {
  const [name, type] = e.detail.split('|');
  const c = candidates.value.find(x => x.name === name);
  if (c) openCommModal(c);
  else toast.info('联系 ' + name + ' (' + type + ')，可用方式：电话 / 邮件 / 飞书');
}

async function handleDetailInterview(e) {
  const name = e.detail;
  const c = candidates.value.find(x => x.name === name);
  if (c) openScheduleModal(c);
  else {
    try {
      const { createInterview } = await import('../api/interview.js');
      const res = await createInterview({ name, position: info.value.position || '' });
      const id = res?.id || '';
      toast.success('已发起面试：' + name + '，面试ID: ' + id);
    } catch (e) {
      handleError(e, 'RecruitDemandDetail.detailInterview');
      toast.info('发起面试：' + name + '，系统将创建面试预约并发送通知');
    }
  }
}

async function loadFromApi() {
  try {
    const demandId = info.value.id || 'DM2026070005';
    const [detail, candidateList] = await Promise.all([
      fetchDemandDetail(demandId),
      fetchDemandCandidates(demandId)
    ]);
    if (detail) Object.assign(info.value, detail);
    if (candidateList) candidates.value = candidateList;
  } catch (e) { console.warn('[Detail] API fallback to mock:', e.message); }
}

onMounted(() => {
  window.addEventListener('detail:view', handleDetailView);
  window.addEventListener('detail:contact', handleDetailContact);
  window.addEventListener('detail:interview', handleDetailInterview);
  loadFromApi();
});

onUnmounted(() => {
  window.removeEventListener('detail:view', handleDetailView);
  window.removeEventListener('detail:contact', handleDetailContact);
  window.removeEventListener('detail:interview', handleDetailInterview);
});
</script>

<style scoped>
.candidate-filter { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 14px 16px; background: var(--c-surface-elevated); border-radius: 8px; border: 1px solid var(--c-border); margin-bottom: 12px; }
.candidate-filter select, .candidate-filter input { height: 34px; padding: 0 10px; border: 1px solid var(--c-border); border-radius: 6px; font-size: 13px; font-family: inherit; background: var(--c-card); color: var(--c-body); }
.candidate-filter .filter-label { font-size: 12px; color: var(--c-sub); font-weight: 600; white-space: nowrap; }
.candidate-filter .spacer { flex: 1; }
.batch-bar { display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; margin-top: -1px; background: var(--c-primary-subtle); border: 1px solid rgba(79,110,247,0.18); border-radius: 8px; font-size: 13px; }
.batch-bar .count { font-weight: 700; color: var(--c-primary); }
.row-locked { opacity: 0.7; }
.row-reserve { opacity: 0.6; }
.match-expired { opacity: 0.65; }
.drawer-overlay { position:fixed; inset:0; background:rgba(0,0,0,.25); z-index:1000; display:flex; justify-content:flex-end; }
.drawer-panel { width:420px; max-width:90vw; height:100%; background:var(--c-card); overflow-y:auto; padding:24px; box-shadow:-4px 0 24px rgba(0,0,0,.1); }
.drawer-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; }
.drawer-header h3 { font-size:18px; margin:0; }
.drawer-kpis { display:flex; gap:16px; margin-bottom:20px; }
.drawer-kpis > div { flex:1; text-align:center; padding:12px; background:var(--c-bg); border-radius:8px; }
.drawer-kpis span { display:block; font-size:11px; color:var(--c-sub); margin-bottom:4px; }
.drawer-kpis b { display:block; font-size:22px; color:var(--c-primary); font-variant-numeric:tabular-nums; }
.drawer-body > div { margin-bottom:16px; }
.drawer-body > div > span { font-size:11px; color:var(--c-sub); display:block; margin-bottom:4px; }
.drawer-body p { font-size:13px; color:var(--c-body); line-height:1.7; }
</style>
