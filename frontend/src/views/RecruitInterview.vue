<template>
  <WorkbenchLayout title="面试计划" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <div style="position:relative">
        <button class="btn btn-ghost btn-sm" id="alertBtn" @click="showAlerts = !showAlerts" style="gap:4px">
          <svg viewBox="0 0 24 24" style="width:16px;height:16px;stroke:var(--c-warn);fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          提醒 <span class="alert-badge">{{ ALERTS_SOURCE.length }}</span>
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
      <button data-testid="open-interview-calendar" class="btn btn-outline btn-sm" @click="focusCalendarCard" style="margin-left:4px" title="查看本周面试日程">
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
    <section class="interview-workbench-grid" aria-label="面试任务流工作台">
      <article
        ref="calendarCardRef"
        data-testid="interview-calendar-card"
        class="interview-workbench-card interview-calendar-card"
        :class="{ 'is-pulsing': calendarPulse }"
      >
        <div class="iw-card-head">
          <div>
            <h3>{{ calendarTitle }}</h3>
            <p>{{ calendarRangeLabel }}</p>
          </div>
          <div class="calendar-nav">
            <div class="cal-toggle-group">
              <button :class="{ active: calView === 'month' }" @click="calView = 'month'">月</button>
              <button :class="{ active: calView === 'week' }" @click="calView = 'week'">周</button>
              <button :class="{ active: calView === 'day' }" @click="calView = 'day'">日</button>
            </div>
            <label class="calendar-select-group">
              <span>月份</span>
              <select data-testid="calendar-month-select" v-model="calendarMonthKey" @change="onCalendarMonthChange">
                <option v-for="item in calendarMonthOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label class="calendar-select-group" v-if="calView === 'week'">
              <span>周期</span>
              <select data-testid="calendar-week-select" v-model="selectedCalendarWeekStart" @change="onCalendarWeekChange">
                <option v-for="item in calendarWeekOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <button data-testid="calendar-current-month" class="btn btn-outline btn-sm" type="button" @click="resetCalendarToday">今天</button>
          </div>
        </div>
        <div class="calendar-range">{{ calendarRangeLabel }}</div>
        <div class="inline-calendar data-region">
          <DataLoadingOverlay :visible="calendarLoading" />

          <!-- 月视图：完整日历网格，风格与周视图一致 -->
          <div v-if="calView === 'month'" class="calendar-month-grid">
            <div class="month-grid-header"><span v-for="d in ['一','二','三','四','五','六','日']" :key="d">{{ d }}</span></div>
            <div class="month-grid-body">
              <button
                v-for="(day, i) in calendarMonthDays" :key="'md'+i"
                v-show="day !== null"
                type="button"
                class="inline-calendar-day month-day-cell"
                :class="{ active: day && selectedCalendarDate === day.key, today: day && day.today }"
                :data-testid="'calendar-day-' + (day ? day.key : '')"
                @click="day && (selectedCalendarDate = day.key)"
              >
                <template v-if="day">
                  <span>周{{ day.day }}</span>
                  <b>{{ day.dateStr }}</b>
                  <em v-if="day.count">{{ day.count }}场</em>
                  <em v-else>—</em>
                </template>
              </button>
            </div>
          </div>

          <!-- 周视图：7 天横向 -->
          <div v-if="calView === 'week'" class="inline-calendar-days" role="list">
            <button
              v-for="day in calendarDays" :key="day.key" type="button"
              class="inline-calendar-day"
              :class="{ active: selectedCalendarDate === day.key, today: day.today, outside: day.outsideMonth }"
              :data-testid="'calendar-day-' + day.key"
              @click="selectedCalendarDate = day.key"
            >
              <span>周{{ day.day }}</span><b>{{ day.dateStr }}</b>
              <em v-if="day.count">{{ day.count }}场</em><em v-else>—</em>
            </button>
          </div>

          <!-- 日视图：当天面试时间线 -->
          <div v-if="calView === 'day'" class="day-timeline">
            <div class="day-timeline-title">{{ selectedCalendarDate }} 周{{ '日一二三四五六'[parseDateKey(selectedCalendarDate).getDay()] }}</div>
            <div v-if="selDayItems.length" class="day-items">
              <div v-for="ev in selDayItems" :key="ev.id" class="day-item">
                <span class="di-time">{{ ev.time || '—' }}</span>
                <span class="di-name">{{ ev.title }}</span>
                <span class="di-pos">{{ ev.position || '' }}</span>
                <a v-if="ev.meetingUrl" :href="ev.meetingUrl" target="_blank" class="meeting-link">{{ ev.method || '会议' }} ↗</a>
                <span v-html="renderActions(ev)" style="margin-left:auto"></span>
              </div>
            </div>
            <div v-else class="workbench-empty">当天暂无面试安排</div>
          </div>

          <!-- 详情列表（月/周视图共用） -->
          <div v-if="calView !== 'day'" class="calendar-detail-list" data-testid="calendar-agenda">
            <div v-for="day in calendarDaySections" :key="'detail-'+day.key" class="calendar-day-section" :class="{ active: selectedCalendarDate === day.key }">
              <div class="calendar-section-title">{{ day.monthLabel }}</div>
              <div class="calendar-table-wrap">
                <table class="calendar-detail-table">
                  <thead><tr><th>候选人</th><th>岗位</th><th>轮次</th><th>时间</th><th>面试官</th><th>方式</th><th>状态</th><th>操作</th></tr></thead>
                  <tbody>
                    <tr v-for="event in day.items" :key="event.id">
                      <td><a href="javascript:void(0)" @click="openCandidateDrawer(event.title)">{{ event.title }}</a></td>
                      <td>{{ event.position || '—' }}</td>
                      <td>{{ event.round || '—' }}</td>
                      <td>{{ event.time }}<span v-if="event.endTime"> - {{ event.endTime }}</span></td>
                      <td>{{ event.interviewer || '—' }}</td>
                      <td>
                        <a v-if="event.meetingUrl" :href="event.meetingUrl" target="_blank" class="meeting-link">{{ event.method || '会议' }} ↗</a>
                        <template v-else>{{ event.method || '—' }}</template>
                      </td>
                      <td><StatusBadge :type="STATUS_TYPE_MAP[event.status]">{{ event.statusLabel || event.status }}</StatusBadge></td>
                      <td style="white-space:nowrap" v-html="renderActions(event)"></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <div v-if="!calendarDaySections.length" class="workbench-empty">{{ calendarLoading ? '日程加载中' : selectedCalendarEmptyText }}</div>
          </div>
        </div>
      </article>
    </section>

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
      <div class="table-wrap data-region">
        <DataLoadingOverlay :visible="loading" />
        <table v-if="filteredList.length > 0"><thead><tr><th style="width:36px"><input data-testid="interview-check-all" type="checkbox" :checked="isAllVisibleSelected(filteredList)" @change="toggleVisible(filteredList, $event)"></th><th>候选人</th><th>岗位</th><th>轮次</th><th>面试官</th><th>时间</th><th>方式</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(item, i) in filteredList" :key="'l'+i">
            <td><input data-testid="interview-row-check" type="checkbox" :checked="!!selectedInterviews[item.id]" @change="toggleInterview(item, $event)"></td>
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
          v-else-if="!loading"
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
      <div class="table-wrap data-region">
        <DataLoadingOverlay :visible="loading" />
        <table v-if="filteredMine.length > 0"><thead><tr><th style="width:36px"><input data-testid="interview-check-all-mine" type="checkbox" :checked="isAllVisibleSelected(filteredMine)" @change="toggleVisible(filteredMine, $event)"></th><th>候选人</th><th>岗位</th><th>轮次</th><th>时间</th><th>方式</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="(item, i) in filteredMine" :key="'m'+i">
            <td><input data-testid="interview-row-check" type="checkbox" :checked="!!selectedInterviews[item.id]" @change="toggleInterview(item, $event)"></td>
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
          v-else-if="!loading"
          title="暂无待办事项"
          description="当前没有需要你处理的面试待办"
        />
        <div class="table-count">共 {{ filteredMine.length }} 条</div>
      </div>
    </div>

    <div data-testid="interview-batch-bar" class="batch-bar interview-batch-bar" v-if="selectedInterviewCount > 0">
      <span>已选择 <b>{{ selectedInterviewCount }}</b> 条面试记录</span>
      <div style="display:flex;gap:8px">
        <button data-testid="interview-delete-selected" class="btn btn-text-danger btn-sm" :disabled="batchDeleting" @click="openBatchDeleteModal">
          {{ batchDeleting ? '删除中...' : '删除选中' }}
        </button>
        <button data-testid="interview-clear-selection" class="btn btn-ghost btn-sm" @click="clearInterviewSelection">清除选择</button>
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

    <!-- Interview Action Modal -->
    <Teleport to="body">
      <div v-if="showActionModal" data-testid="interview-action-modal" class="modal-overlay" @click.self="closeActionModal" style="display:flex">
        <div class="modal-box interview-action-modal" role="dialog" aria-modal="true" aria-label="面试操作确认">
          <div class="modal-head">
            <div>
              <h3>{{ actionModalTitle }}</h3>
              <p>{{ actionModalDesc }}</p>
            </div>
            <button class="btn btn-ghost btn-sm" @click="closeActionModal">关闭</button>
          </div>

          <div v-if="actionMode === 'complete'" class="action-options" role="radiogroup" aria-label="完成面试结果">
            <button
              v-for="opt in completeOptions"
              :key="opt.value"
              data-testid="interview-action-option"
              class="action-option"
              :class="{ active: actionForm.arrive === opt.value }"
              type="button"
              role="radio"
              :aria-checked="actionForm.arrive === opt.value ? 'true' : 'false'"
              @click="actionForm.arrive = opt.value"
            >
              <b>{{ opt.label }}</b>
              <span>{{ opt.desc }}</span>
            </button>
          </div>

          <div v-else class="action-options" role="radiogroup" aria-label="取消或删除原因">
            <button
              v-for="opt in cancelReasons"
              :key="opt.value"
              data-testid="interview-action-option"
              class="action-option"
              :class="{ active: actionForm.reason === opt.value }"
              type="button"
              role="radio"
              :aria-checked="actionForm.reason === opt.value ? 'true' : 'false'"
              @click="actionForm.reason = opt.value"
            >
              <b>{{ opt.label }}</b>
              <span>{{ opt.desc }}</span>
            </button>
          </div>

          <div class="modal-actions">
            <button class="btn btn-ghost btn-sm" @click="closeActionModal">取消</button>
            <button
              data-testid="interview-action-confirm"
              class="btn btn-sm"
              :class="actionMode === 'complete' ? 'btn-primary' : 'btn-text-danger'"
              :disabled="actionSaving"
              @click="submitActionModal"
            >
              {{ actionSaving ? '处理中...' : actionConfirmText }}
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
import { STATUS_TYPE_MAP } from '../data/interview.js';
import { fetchInterviews, fetchInterviewAlerts, createInterview, evaluateInterview, completeInterview, cancelInterview, fetchInterviewCalendar } from '../api/interview.js';
import { useToast } from '../composables/useToast.js';
import { useAppError } from '../composables/useAppError.js';
import { KPI_ICONS } from '../components/kpiIcons.js';
import ScheduleInterviewModal from '../components/ScheduleInterviewModal.vue';
import OfferModal from '../components/OfferModal.vue';
import EmptyState from '../components/EmptyState.vue';
import CandidateDrawer from '../components/CandidateDrawer.vue';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';

const { toast } = useToast();
const { handleError } = useAppError();

const showAlerts = ref(false);
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
const loading = ref(true);
const loadError = ref('');
const selectedInterviews = reactive({});
const batchDeleting = ref(false);
const selectedInterviewCount = computed(() => Object.keys(selectedInterviews).filter(k => selectedInterviews[k]).length);
const showActionModal = ref(false);
const actionMode = ref('complete');
const actionTarget = ref({ id: '', name: '', ids: [] });
const actionForm = reactive({ arrive: 1, reason: 'HR 手动取消' });
const actionSaving = ref(false);
const calendarCardRef = ref(null);
const calendarPulse = ref(false);
const calendarLoading = ref(false);
const calendarData = ref({ month: '', monthStart: '', monthEnd: '', events: [] });
const calendarMonthKey = ref(formatMonthKey(new Date()));
const selectedCalendarWeekStart = ref(getWeekStartKey(new Date()));
const selectedCalendarDate = ref(formatDateKey(new Date()));
const calView = ref('week'); // month | week | day

function resetCalendarToday() {
  calendarMonthKey.value = formatMonthKey(new Date());
  selectedCalendarWeekStart.value = getWeekStartKey(new Date());
  selectedCalendarDate.value = formatDateKey(new Date());
  onCalendarMonthChange();
}

const INTERVIEWS_SOURCE = computed(() => apiInterviewData.value ?? []);
const ALERTS_SOURCE = computed(() => apiAlertData.value ?? []);

const visibleTabs = computed(() => {
  const tabs = [];
  if (!isInterviewerRole) tabs.push({ id: 'list', label: '全部面试' });
  tabs.push({ id: 'mine', label: '我的待办' });
  return tabs;
});

if (isInterviewerRole) activeTab.value = 'mine';

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

function toggleInterview(item, e) {
  if (!item?.id) return;
  selectedInterviews[item.id] = e.target.checked;
}

function isAllVisibleSelected(list) {
  const ids = list.map(i => i.id).filter(Boolean);
  return ids.length > 0 && ids.every(id => selectedInterviews[id]);
}

function toggleVisible(list, e) {
  list.forEach(item => {
    if (item?.id) selectedInterviews[item.id] = e.target.checked;
  });
}

function clearInterviewSelection() {
  Object.keys(selectedInterviews).forEach(k => delete selectedInterviews[k]);
}

function openBatchDeleteModal() {
  const ids = Object.keys(selectedInterviews).filter(k => selectedInterviews[k]);
  if (!ids.length) return;
  actionMode.value = 'batch-delete';
  actionTarget.value = { id: '', name: '', ids };
  Object.assign(actionForm, { arrive: 1, reason: 'HR 批量删除' });
  showActionModal.value = true;
}

async function deleteSelectedInterviews(ids, reason) {
  if (!ids.length) return;
  batchDeleting.value = true;
  let ok = 0;
  let fail = 0;
  try {
    for (const id of ids) {
      try {
        await cancelInterview(id, reason || 'HR 批量删除');
        ok++;
      } catch (e) {
        console.warn('[RecruitInterview] batch delete failed:', id, e);
        fail++;
      }
    }
    toast[fail ? 'warning' : 'success']('删除完成：成功 ' + ok + ' 条，失败 ' + fail + ' 条');
    clearInterviewSelection();
    await loadFromApi();
  } finally {
    batchDeleting.value = false;
  }
}

function renderActions(item) {
  const resumeBtn = '<button class="btn btn-outline btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:open-drawer\',{detail:\'' + item.name + '\'}))">简历</button>';
  switch (item.status) {
    case 'pending':
      return resumeBtn + ' <button class="btn btn-primary btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:schedule\',{detail:\'' + item.name + '|' + item.position + '\'}))">发起面试</button>';
    case 'scheduled':
      return resumeBtn + ' <button class="btn btn-primary btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:complete\',{detail:\'' + item.id + '|' + item.name + '\'}))">完成面试</button> <button class="btn btn-text-danger btn-sm" onclick="window.dispatchEvent(new CustomEvent(\'interview:cancel\',{detail:\'' + item.id + '|' + item.name + '\'}))">取消</button>';
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
  openCompleteModal(id, name);
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
  const parts = String(e.detail).split('|');
  const id = parts.length > 1 ? parts[0] : '';
  const name = parts.length > 1 ? parts[1] : parts[0];
  const item = id
    ? INTERVIEWS_SOURCE.value.find(i => i.id === id)
    : INTERVIEWS_SOURCE.value.find(i => i.name === name);
  if (!item || !item.id) {
    toast.error('该记录未同步到服务端（可能为本地演示数据），无法取消');
    return;
  }
  openCancelModal(item.id, item.name || name);
}

function openCompleteModal(id, name) {
  actionMode.value = 'complete';
  actionTarget.value = { id, name, ids: [] };
  Object.assign(actionForm, { arrive: 1, reason: 'HR 手动取消' });
  showActionModal.value = true;
}

function openCancelModal(id, name) {
  actionMode.value = 'cancel';
  actionTarget.value = { id, name, ids: [] };
  Object.assign(actionForm, { arrive: 1, reason: '候选人改期' });
  showActionModal.value = true;
}

function closeActionModal() {
  if (actionSaving.value) return;
  showActionModal.value = false;
}

const completeOptions = [
  { value: 1, label: '候选人已到场', desc: '确认面试完成，下一步进入待评价状态' },
  { value: 0, label: '候选人未到场', desc: '记录未到场，仍进入待评价留痕' },
];

const cancelReasons = [
  { value: '候选人改期', label: '候选人改期', desc: '候选人需要重新协调面试时间' },
  { value: '候选人取消', label: '候选人取消', desc: '候选人主动取消本次面试' },
  { value: 'HR 手动取消', label: 'HR 手动取消', desc: '招聘侧主动取消本次安排' },
  { value: 'HR 批量删除', label: '批量删除', desc: '清理选中的面试记录' },
];

const actionModalTitle = computed(() => {
  if (actionMode.value === 'complete') return '确认面试结果';
  if (actionMode.value === 'batch-delete') return '删除选中的面试记录';
  return '取消面试安排';
});

const actionModalDesc = computed(() => {
  if (actionMode.value === 'complete') return `${actionTarget.value.name} 的面试将按所选结果更新。`;
  if (actionMode.value === 'batch-delete') return `将删除 ${actionTarget.value.ids.length} 条已选面试记录，请选择删除原因。`;
  return `${actionTarget.value.name} 的面试将被取消，请选择原因。`;
});

const actionConfirmText = computed(() => {
  if (actionMode.value === 'complete') return '确认完成';
  if (actionMode.value === 'batch-delete') return '确认删除';
  return '确认取消面试';
});

async function submitActionModal() {
  actionSaving.value = true;
  try {
    if (actionMode.value === 'complete') {
      await completeInterview(actionTarget.value.id, { is_arrive: actionForm.arrive });
      const suffix = actionForm.arrive ? '已进入待评价，请面试官提交评价' : '已记录未到场，请继续补充评价说明';
      toast.success('【面试完成】' + actionTarget.value.name + ' ' + suffix);
      await loadFromApi();
    } else if (actionMode.value === 'batch-delete') {
      await deleteSelectedInterviews(actionTarget.value.ids, actionForm.reason);
    } else {
      await cancelInterview(actionTarget.value.id, actionForm.reason);
      toast.success('已取消 ' + actionTarget.value.name + ' 的面试');
      await loadFromApi();
    }
    showActionModal.value = false;
  } catch (err) {
    const source = actionMode.value === 'complete' ? 'RecruitInterview.handleComplete' : 'RecruitInterview.handleCancel';
    handleError(err, source);
    toast.error('操作失败：' + (err?.response?.data?.message || err.message || '未知错误'));
  } finally {
    actionSaving.value = false;
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
  loading.value = true;
  calendarLoading.value = true;
  loadError.value = '';
  try {
    const [listRes, alertRes, calendarRes] = await Promise.all([
      fetchInterviews({ tab: activeTab.value }),
      fetchInterviewAlerts(),
      fetchInterviewCalendar({ month: calendarMonthKey.value })
    ]);
    apiInterviewData.value = Array.isArray(listRes?.data) ? listRes.data : (Array.isArray(listRes) ? listRes : []);
    apiAlertData.value = Array.isArray(alertRes) ? alertRes : [];
    applyCalendarData(calendarRes);
  } catch (e) {
    loadError.value = e.message || '面试数据加载失败';
    apiInterviewData.value = [];
    apiAlertData.value = [];
    calendarData.value = {
      month: calendarMonthKey.value,
      monthStart: getMonthStartKey(calendarMonthKey.value),
      monthEnd: getMonthEndKey(calendarMonthKey.value),
      events: [],
    };
    console.warn('[Interview] API fetch failed:', e.message);
  } finally {
    loading.value = false;
    calendarLoading.value = false;
  }
}

function formatDateKey(date) {
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
}

function formatMonthKey(date) {
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0');
}

function parseDateKey(key) {
  const [year, month, day] = String(key || '').split('-').map(Number);
  if (!year || !month || !day) return new Date();
  return new Date(year, month - 1, day);
}

function parseMonthKey(key) {
  const [year, month] = String(key || '').split('-').map(Number);
  if (!year || !month) return new Date();
  return new Date(year, month - 1, 1);
}

function getWeekStartKey(date) {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return formatDateKey(d);
}

function addDateDays(dateKey, days) {
  const d = parseDateKey(dateKey);
  d.setDate(d.getDate() + days);
  return formatDateKey(d);
}

function getMonthStartKey(monthKey) {
  return `${monthKey}-01`;
}

function getMonthEndKey(monthKey) {
  const d = parseMonthKey(monthKey);
  d.setMonth(d.getMonth() + 1);
  d.setDate(0);
  return formatDateKey(d);
}

function formatMonthDay(key) {
  const d = parseDateKey(key);
  return `${d.getMonth() + 1}/${String(d.getDate()).padStart(2, '0')}`;
}

function getDefaultWeekStartForMonth(monthKey) {
  const todayKey = formatDateKey(new Date());
  const monthStart = getMonthStartKey(monthKey);
  const monthEnd = getMonthEndKey(monthKey);
  if (todayKey >= monthStart && todayKey <= monthEnd) return getWeekStartKey(parseDateKey(todayKey));
  return getWeekStartKey(parseDateKey(monthStart));
}

function getDefaultDateForWeek(weekStart, monthKey) {
  const monthStart = getMonthStartKey(monthKey);
  const monthEnd = getMonthEndKey(monthKey);
  for (let i = 0; i < 7; i++) {
    const key = addDateDays(weekStart, i);
    if (key >= monthStart && key <= monthEnd) return key;
  }
  return weekStart;
}

function normalizeCalendarEvent(event) {
  const start = event?.start || '';
  const end = event?.end || '';
  const name = event?.title || event?.name || '候选人待定';
  return {
    ...event,
    name, title: name,
    dateKey: start.slice(0, 10),
    time: start.slice(11, 16) || '待定',
    endTime: end.slice(11, 16),
    statusLabel: event?.statusLabel || '',
  };
}

function applyCalendarData(res) {
  const data = (res?.monthStart || res?.weekStart) ? res : (res?.data || {});
  const month = data.month || formatMonthKey(parseDateKey(data.monthStart || data.weekStart || getMonthStartKey(calendarMonthKey.value)));
  const monthStart = data.monthStart || data.weekStart || getMonthStartKey(month);
  const monthEnd = data.monthEnd || data.weekEnd || getMonthEndKey(month);
  calendarMonthKey.value = month;
  calendarData.value = {
    month,
    monthStart,
    monthEnd,
    events: Array.isArray(data.events) ? data.events : [],
  };
  const validDays = new Set(calendarDays.value.map(d => d.key));
  if (!validDays.has(selectedCalendarDate.value)) {
    selectedCalendarDate.value = getDefaultDateForWeek(selectedCalendarWeekStart.value, month);
  }
}

async function loadCalendar(month = calendarMonthKey.value) {
  calendarLoading.value = true;
  try {
    const res = await fetchInterviewCalendar({ month });
    applyCalendarData(res);
  } catch (e) {
    calendarData.value = {
      month,
      monthStart: getMonthStartKey(month),
      monthEnd: getMonthEndKey(month),
      events: [],
    };
    toast.error('日程加载失败：' + (e?.message || '未知错误'));
  } finally {
    calendarLoading.value = false;
  }
}

function onCalendarMonthChange() {
  selectedCalendarWeekStart.value = getDefaultWeekStartForMonth(calendarMonthKey.value);
  selectedCalendarDate.value = getDefaultDateForWeek(selectedCalendarWeekStart.value, calendarMonthKey.value);
  loadCalendar(calendarMonthKey.value);
}

function onCalendarWeekChange() {
  selectedCalendarDate.value = getDefaultDateForWeek(selectedCalendarWeekStart.value, calendarMonthKey.value);
}

function resetCalendarMonth() {
  const current = formatMonthKey(new Date());
  calendarMonthKey.value = current;
  selectedCalendarWeekStart.value = getWeekStartKey(new Date());
  selectedCalendarDate.value = formatDateKey(new Date());
  loadCalendar(current);
}

function focusCalendarCard() {
  calendarCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  calendarPulse.value = true;
  window.setTimeout(() => { calendarPulse.value = false; }, 900);
}

const calendarMonthOptions = computed(() => {
  const year = new Date().getFullYear();
  return Array.from({ length: 12 }, (_, index) => {
    const month = index + 1;
    return {
      value: `${year}-${String(month).padStart(2, '0')}`,
      label: `${year}年${month}月`,
    };
  });
});

const calendarWeekOptions = computed(() => {
  const monthStart = getMonthStartKey(calendarMonthKey.value);
  const monthEnd = getMonthEndKey(calendarMonthKey.value);
  let cursor = getWeekStartKey(parseDateKey(monthStart));
  const options = [];
  let index = 1;
  while (cursor <= monthEnd) {
    const end = addDateDays(cursor, 6);
    options.push({
      value: cursor,
      label: `第${index}周（${formatMonthDay(cursor)} - ${formatMonthDay(end)}）`,
    });
    cursor = addDateDays(cursor, 7);
    index++;
  }
  return options;
});

const calendarEvents = computed(() => {
  return (calendarData.value.events || [])
    .map(normalizeCalendarEvent)
    .filter(event => event.dateKey)
    .sort((a, b) => (a.start || '').localeCompare(b.start || ''));
});

const calendarDays = computed(() => {
  const monthStartKey = calendarData.value.monthStart || getMonthStartKey(calendarMonthKey.value);
  const monthEndKey = calendarData.value.monthEnd || getMonthEndKey(calendarMonthKey.value);
  const weekStart = parseDateKey(selectedCalendarWeekStart.value || getDefaultWeekStartForMonth(calendarMonthKey.value));
  const days = ['一','二','三','四','五','六','日'];
  const todayKey = formatDateKey(new Date());
  const calData = {};
  calendarEvents.value.forEach(item => {
    const k = item.dateKey;
    if (!calData[k]) calData[k] = [];
    calData[k].push(item);
  });
  const result = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(weekStart);
    d.setDate(weekStart.getDate() + i);
    const key = d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
    const items = calData[key] || [];
    const dayIndex = (d.getDay() + 6) % 7;
    result.push({
      key, items, count: items.length, today: key === todayKey,
      outsideMonth: key < monthStartKey || key > monthEndKey,
      day: days[dayIndex], dateStr: String(d.getDate()).padStart(2,'0'),
      monthLabel: (d.getMonth()+1) + '/' + String(d.getDate()).padStart(2,'0') + ' 周' + days[dayIndex] + ' · ' + items.length + '场'
    });
  }
  return result;
});

const calendarMonthDays = computed(() => {
  const monthStartKey = calendarData.value.monthStart || getMonthStartKey(calendarMonthKey.value);
  const monthEndKey = calendarData.value.monthEnd || getMonthEndKey(calendarMonthKey.value);
  const start = parseDateKey(monthStartKey);
  const end = parseDateKey(monthEndKey);
  const todayKey = formatDateKey(new Date());
  const days = ['一','二','三','四','五','六','日'];
  // 填充到周一
  const firstDow = (start.getDay() + 6) % 7;
  const calData = {};
  calendarEvents.value.forEach(item => {
    const k = item.dateKey;
    if (!calData[k]) calData[k] = [];
    calData[k].push(item);
  });
  const result = [];
  // 前置空白
  for (let i = 0; i < firstDow; i++) result.push(null);
  const cursor = new Date(start);
  while (formatDateKey(cursor) <= monthEndKey) {
    const key = formatDateKey(cursor);
    const items = calData[key] || [];
    const dayIndex = (cursor.getDay() + 6) % 7;
    result.push({
      key, items, count: items.length, today: key === todayKey,
      day: days[dayIndex], dateStr: String(cursor.getDate()).padStart(2, '0'),
      monthLabel: (cursor.getMonth() + 1) + '/' + String(cursor.getDate()).padStart(2, '0') + ' 周' + days[dayIndex] + ' · ' + items.length + '场'
    });
    cursor.setDate(cursor.getDate() + 1);
  }
  return result;
});

const selectedCalendarDay = computed(() => {
  const pool = calView.value === 'month' ? calendarMonthDays.value : calendarDays.value;
  return pool.find(day => day && day.key === selectedCalendarDate.value) || pool.find(d => d) || null;
});

const selDayItems = computed(() => {
  const day = selectedCalendarDay.value;
  return day ? calendarEvents.value.filter(e => e.dateKey === day.key) : [];
});

const calendarDaySections = computed(() => {
  const day = selectedCalendarDay.value;
  return day && day.count > 0 ? [day] : [];
});

const selectedCalendarEmptyText = computed(() => {
  const day = selectedCalendarDay.value;
  if (!day) return '暂无面试安排';
  if (calView.value === 'month') return `${day.key} 暂无面试安排`;
  return day.monthLabel ? `${day.monthLabel.replace(/ · 0场$/, '')} 暂无面试安排` : '本周暂无面试安排';
});

const calendarRangeLabel = computed(() => {
  if (calView.value === 'month') {
    const start = calendarData.value.monthStart || getMonthStartKey(calendarMonthKey.value);
    const end = calendarData.value.monthEnd || getMonthEndKey(calendarMonthKey.value);
    return `${formatMonthDay(start)} - ${formatMonthDay(end)} · 本月 ${calendarEvents.value.length} 场`;
  }
  if (calView.value === 'day') {
    const d = parseDateKey(selectedCalendarDate.value);
    return `${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日 周${'日一二三四五六'[d.getDay()]} · ${selDayItems.value.length} 场`;
  }
  const start = calendarDays.value[0]?.key || (calendarData.value.monthStart || getMonthStartKey(calendarMonthKey.value));
  const end = calendarDays.value[6]?.key || start;
  const weekCount = calendarDays.value.reduce((sum, day) => sum + day.count, 0);
  return `${formatMonthDay(start)} - ${formatMonthDay(end)} · 本周 ${weekCount} 场 / 本月 ${calendarEvents.value.length} 场`;
});

const calendarTitle = computed(() => {
  const start = parseMonthKey(calendarData.value.month || calendarMonthKey.value);
  return `面试日程 · ${start.getFullYear()}年${start.getMonth() + 1}月`;
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
.data-region { position: relative; min-height: 220px; }
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

.interview-workbench-grid {
  display: grid;
  grid-template-columns: 1fr;
  margin-bottom: 16px;
}
.interview-workbench-card {
  min-height: 360px;
  padding: 18px 20px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-card);
  box-shadow: 0 1px 2px rgba(15, 23, 42, .03);
}
.iw-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.iw-card-head h3 {
  margin: 0;
  color: var(--c-text);
  font-size: 16px;
  line-height: 1.35;
}
.iw-card-head p {
  margin: 4px 0 0;
  color: var(--c-sub);
  font-size: 12px;
  line-height: 1.5;
}
.interview-calendar-card {
  scroll-margin-top: 80px;
  transition: border-color .18s, box-shadow .18s;
}
.interview-calendar-card.is-pulsing {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 4px rgba(79, 110, 247, .14);
}
.calendar-nav {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}
.calendar-select-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--c-sub);
  font-size: 12px;
  font-weight: 700;
}
.calendar-select-group select {
  height: 30px;
  min-width: 112px;
  padding: 0 26px 0 10px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-card);
  color: var(--c-text);
  font: inherit;
  font-weight: 700;
  outline: none;
}
.calendar-select-group select:focus {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(79, 110, 247, .12);
}
.calendar-range {
  margin: -4px 0 10px;
  color: var(--c-text);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.inline-calendar {
  min-height: 260px;
}
.inline-calendar-days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}
.inline-calendar-day {
  min-width: 0;
  min-height: 72px;
  padding: 9px 6px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  background: var(--c-bg);
  color: var(--c-text);
  cursor: pointer;
  font-family: inherit;
  text-align: center;
}
.inline-calendar-day:hover,
.inline-calendar-day.active {
  border-color: var(--c-primary);
  background: var(--c-primary-subtle);
}
.inline-calendar-day.today {
  box-shadow: inset 0 0 0 1px var(--c-primary);
}
.inline-calendar-day.outside {
  opacity: .56;
}
.inline-calendar-day span,
.inline-calendar-day em {
  display: block;
  overflow: hidden;
  color: var(--c-sub);
  font-size: 11px;
  font-style: normal;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.inline-calendar-day b {
  display: block;
  margin: 3px 0;
  font-size: 20px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.inline-calendar-day.active em {
  color: var(--c-primary);
  font-weight: 700;
}
.calendar-detail-list {
  display: grid;
  gap: 16px;
}
.calendar-day-section {
  border: 1px solid transparent;
  border-radius: 8px;
  transition: border-color .15s, background .15s;
}
.calendar-day-section.active {
  border-color: rgba(79, 110, 247, .28);
  background: rgba(79, 110, 247, .03);
}
.calendar-section-title {
  padding: 0 2px 8px;
  color: var(--c-text);
  font-size: 14px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.calendar-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--c-border);
  border-radius: 8px;
}
.calendar-detail-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
  font-size: 12px;
}
.calendar-detail-table th,
.calendar-detail-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--c-border);
  color: var(--c-text);
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
}
.calendar-detail-table th {
  background: var(--c-bg);
  color: var(--c-sub);
  font-size: 11px;
  font-weight: 700;
}
.calendar-detail-table th::after {
  content: "↕";
  margin-left: 4px;
  color: var(--c-muted);
  font-size: 10px;
}
.calendar-detail-table tr:last-child td {
  border-bottom: 0;
}
.calendar-detail-table a {
  color: var(--c-primary);
  font-weight: 700;
  text-decoration: none;
}
.calendar-detail-table a:hover {
  text-decoration: underline;
}
.calendar-detail-table td span {
  color: var(--c-sub);
}
.workbench-empty {
  display: grid;
  min-height: 120px;
  place-items: center;
  color: var(--c-sub);
  font-size: 13px;
}

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
.interview-batch-bar {
  display:flex;
  align-items:center;
  justify-content:space-between;
  padding:10px 16px;
  margin-top:12px;
  border:1px solid rgba(79,110,247,.18);
  border-radius:8px;
  background:var(--c-primary-subtle);
  font-size:13px;
}
.interview-batch-bar b { color:var(--c-primary); font-variant-numeric:tabular-nums; }

.interview-action-modal {
  width: 520px;
  max-width: 92vw;
}
.modal-head {
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap:16px;
  margin-bottom:14px;
}
.modal-head h3 {
  margin:0 0 4px;
  font-size:18px;
}
.modal-head p {
  margin:0;
  color:var(--c-sub);
  font-size:13px;
  line-height:1.6;
}
.action-options {
  display:grid;
  gap:8px;
  margin-bottom:16px;
}
.action-option {
  width:100%;
  text-align:left;
  padding:12px 14px;
  border:1px solid var(--c-border);
  border-radius:8px;
  background:var(--c-card);
  color:var(--c-text);
  cursor:pointer;
  font-family:inherit;
}
.action-option:hover {
  border-color:var(--c-primary);
}
.action-option.active {
  border-color:var(--c-primary);
  background:var(--c-primary-subtle);
  box-shadow:0 0 0 3px rgba(79,110,247,.12);
}
.action-option b {
  display:block;
  font-size:14px;
  margin-bottom:4px;
}
.action-option span {
  display:block;
  color:var(--c-sub);
  font-size:12px;
  line-height:1.5;
}

@media (max-width: 1200px) {
  .iv-stat-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .interview-workbench-grid { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .iv-stat-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .interview-workbench-card { padding: 14px; }
  .inline-calendar-days { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}

/* ── 筛选行 ── */
.iv-filter-row { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; flex-wrap: wrap; }
.iv-filter-label { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--c-body); }
.iv-filter-label span { font-weight: 600; white-space: nowrap; }
.iv-filter-label select { padding: 5px 10px; border: 1px solid var(--c-border); border-radius: 6px; font-size: 13px; background: var(--c-card); color: var(--c-text); }
.iv-filter-count { flex: 1; text-align: right; font-size: 11px; color: var(--c-sub); }

/* ── 日历视图切换 ── */
.cal-view-group { display: flex; align-items: center; gap: 4px; }
.cal-view-label { font-size: 13px; font-weight: 700; color: var(--c-text); min-width: 140px; text-align: center; }
.cal-toggle-group { display: flex; border: 1px solid var(--c-border); border-radius: 6px; overflow: hidden; }
.cal-toggle-group button { padding: 4px 12px; border: none; background: var(--c-card); color: var(--c-body); font-size: 12px; font-weight: 600; cursor: pointer; }
.cal-toggle-group button + button { border-left: 1px solid var(--c-border); }
.cal-toggle-group button.active { background: var(--c-primary); color: #fff; }

/* ── 月视图网格 ── */
.calendar-month-grid { margin-top: 8px; }
.month-grid-header { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; font-size: 12px; color: var(--c-sub); font-weight: 600; padding: 4px 0; }
.month-grid-body { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; }
.month-day-cell { aspect-ratio: unset; padding: 8px 4px; }

/* ── 日视图时间线 ── */
.day-timeline { margin-top: 8px; }
.day-timeline-title { font-size: 14px; font-weight: 700; color: var(--c-text); margin-bottom: 10px; }
.day-items { display: flex; flex-direction: column; gap: 6px; }
.day-item {
  display: flex; align-items: center; gap: 12px; padding: 10px 14px;
  border: 1px solid var(--c-border); border-radius: 8px; background: var(--c-card);
}
.di-time { font-size: 14px; font-weight: 700; color: var(--c-primary); min-width: 48px; }
.di-name { font-size: 14px; font-weight: 600; color: var(--c-text); }
.di-pos { font-size: 12px; color: var(--c-sub); }
</style>
