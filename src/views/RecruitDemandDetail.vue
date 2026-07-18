<template>
  <WorkbenchLayout title="需求详情" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <StatusBadge type="progress">招聘中</StatusBadge>
      <button class="btn btn-outline btn-sm" style="margin-left:8px" @click="doAlert('重新匹配（demo）')">重新匹配</button>
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
          <span style="color:var(--c-done)">✓</span> {{ node.role }} <b>{{ node.actor }}</b> — {{ node.status }} · {{ node.date }}
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
        <table id="candidateTable" style="min-width:860px"><thead><tr>
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
            <td style="white-space:nowrap" v-html="actionCell(c)"></td>
          </tr>
        </tbody></table>
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
  </WorkbenchLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import { DEMAND_INFO, ALL_CANDIDATES, CANDIDATE_META } from '../data/demand-detail.js';
import { fetchDemandDetail, fetchDemandCandidates } from '../api/demand.js';

const info = ref(DEMAND_INFO);
const candidates = ref([...ALL_CANDIDATES]);

async function loadDemandDetail() {
  try {
    const res = await fetchDemandDetail();
    if (res) info.value = res;
  } catch (e) {
    console.warn('[RecruitDemandDetail] fetchDemandDetail failed, using mock data:', e);
  }

  try {
    const res = await fetchDemandCandidates();
    if (res) candidates.value = res;
  } catch (e) {
    console.warn('[RecruitDemandDetail] fetchDemandCandidates failed, using mock data:', e);
  }
}

const checkedSet = reactive({});
const checkedCount = computed(() => Object.keys(checkedSet).filter(k => checkedSet[k]).length);

const filters = reactive({
  source: 'all', score: '0', match: 'all', status: 'all',
  age: 'all', edu: 'all', years: 'all', profile: '0', keyword: ''
});

const filteredCandidates = computed(() => {
  let list = candidates.value.filter(c => {
    const meta = CANDIDATE_META[c.name] || { edu: '本科', years: '3-5' };
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
    if (filters.edu !== 'all' && meta.edu !== filters.edu) return false;
    if (filters.years !== 'all' && meta.years !== filters.years) return false;
    if (filters.keyword && c.name.indexOf(filters.keyword) < 0) return false;
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

// Helpers
function matchColor(s) { if (s >= 80) return 'var(--c-done)'; if (s >= 60) return 'var(--c-warn)'; return 'var(--c-draft)'; }
function profileColor(s) { if (s >= 80) return 'score-high'; if (s >= 60) return 'score-mid'; return 'score-low'; }
function ageLabel(d) { if (d >= 365) return Math.floor(d / 365) + '年'; return d + '天'; }
function rowClass(c) {
  if (c.status === 'interviewing') return 'row-locked';
  if (c.notRecReason && c.notRecReason.indexOf('学历') >= 0) return 'row-reserve';
  if (c.notRecReason && c.notRecReason.indexOf('超期') >= 0) return 'match-expired';
  return '';
}

function actionCell(c) {
  if (c.isEmployee) {
    const viewBtn = '<button class="btn btn-outline btn-sm" onclick="window.alert(\'员工抽屉：' + c.name + '\')">查看</button>';
    if (c.notRecReason) return '<span style="font-size:11px;color:var(--c-draft)">' + c.notRecReason + '</span>';
    return viewBtn + ' <button class="btn btn-outline btn-sm" onclick="window.alert(\'联系：' + c.name + '（直属上级）\')">联系</button> <button class="btn btn-primary btn-sm" onclick="window.alert(\'发起内部面试：' + c.name + '\')">发起面试</button>';
  }
  const viewBtn = '<button class="btn btn-outline btn-sm" onclick="window.alert(\'候选人抽屉：' + c.name + '\')">查看</button>';
  if (c.status === 'interviewing') return viewBtn + ' <span style="font-size:11px;color:var(--c-sub)">面试中</span>';
  if (c.notRecReason) return viewBtn + ' <span style="font-size:11px;color:var(--c-draft)">' + c.notRecReason + '</span>';
  if (c.matchScore && c.matchScore >= 60) return '<button class="btn btn-outline btn-sm" onclick="window.alert(\'联系：' + c.name + '\')">联系</button> <button class="btn btn-primary btn-sm" onclick="window.alert(\'约面：' + c.name + ' - 高级Java工程师 - 技术部\')">约面</button>';
  return '<span style="font-size:11px;color:var(--c-draft)">匹配分不足</span>';
}

function applyFilter() {}
function toggleAll(e) {
  filteredCandidates.value.forEach(c => { checkedSet[c.name] = e.target.checked; });
}
function onCheck() {}
function clearSelection() {
  Object.keys(checkedSet).forEach(k => delete checkedSet[k]);
}
function openDrawer(c) {
  if (c.isEmployee) doAlert('员工抽屉：' + c.name);
  else doAlert('候选人抽屉：' + c.name);
}

// Batch actions
function batchContact() { batchAlert('批量联系'); }
function addToDemand() {
  const names = Object.keys(checkedSet).filter(k => checkedSet[k]);
  if (!names.length) { alert('请先勾选候选人'); return; }
  const key = 'demand_DM2026070005_linked';
  const linked = (() => { try { return JSON.parse(localStorage.getItem(key)) || []; } catch(e) { return []; } })();
  names.forEach(n => { if (linked.indexOf(n) < 0) linked.push(n); });
  localStorage.setItem(key, JSON.stringify(linked));
  alert('已将 ' + names.length + ' 位候选人加入需求「高级Java工程师」\n\n' + names.join('、'));
  clearSelection();
}
function batchMoveDemand() { batchAlert('批量移出需求'); }
function batchSchedule() { batchAlert('批量约面'); }
function batchMarkUnsuitable() { batchAlert('标记不合适'); }
function batchExport() { batchAlert('导出'); }
function batchAlert(label) {
  const names = Object.keys(checkedSet).filter(k => checkedSet[k]);
  if (!names.length) { alert('请先勾选候选人'); return; }
  alert(label + ' ' + names.length + ' 人\n\n' + names.join('、'));
}
function doAlert(msg) { window.alert(msg); }

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

onMounted(() => { loadFromApi(); });
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
</style>