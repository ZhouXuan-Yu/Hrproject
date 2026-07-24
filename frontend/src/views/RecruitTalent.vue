<template>
  <WorkbenchLayout title="人才库" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <!-- Reminder dropdown -->
      <div style="position:relative">
        <button class="btn btn-ghost btn-sm" id="reminderBtn" @click="showReminder = !showReminder" style="gap:4px">
          <svg viewBox="0 0 24 24" style="width:16px;height:16px;stroke:var(--c-warn);fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg> 提醒
        </button>
        <div id="reminderDropdown" v-if="showReminder" style="display:block;position:absolute;top:100%;right:0;margin-top:6px;width:320px;background:var(--c-card);border:1px solid var(--c-border);border-radius:12px;padding:16px;box-shadow:0 8px 32px rgba(0,0,0,.12);z-index:100;font-size:13px;line-height:2">
          <div style="font-weight:700;margin-bottom:8px;color:var(--c-text)">合规 & 提醒</div>
          <div style="color:var(--c-body)">&bull; 黑名单候选人自动置灰屏蔽，全局不可操作</div>
          <div style="color:var(--c-body)">&bull; 入库满 24 个月自动失效封存</div>
          <div style="color:var(--c-body)">&bull; 过期证书/技能标签自动标记失效</div>
          <div style="color:var(--c-body)">&bull; 已锁定（面试中）候选人不可重复发起</div>
          <div style="color:var(--c-body);margin-top:4px">&bull; 本页面仅 <b>HR 专员</b>和<b>系统管理员</b>可见</div>
        </div>
      </div>
      <button class="btn btn-primary btn-sm" :disabled="uploading" @click="uploadResume">
        {{ uploading ? '解析中...' : '+ 上传简历' }}
      </button>
      <input ref="resumeFileInput" type="file" accept=".pdf,.docx,.txt,.md,.html" style="display:none" @change="onResumeFilePicked">
    </template>

    <!-- 3 Tabs -->
    <div class="tabs" role="tablist">
      <button v-for="tab in tabs" :key="tab.id"
        class="tab" :class="{ active: activeTab === tab.id }"
        :aria-selected="activeTab === tab.id ? 'true' : 'false'"
        role="tab" @click="activeTab = tab.id"
      >{{ tab.label }}</button>
      <button
        v-if="activeTab === 'external'"
        class="btn btn-outline btn-sm mail-sync-btn"
        :disabled="mailSyncing"
        @click="manualMailSync"
        title="手动收取所有启用邮箱中的简历邮件（定时任务每 30 分钟自动执行）"
        aria-label="手动刷新邮箱简历"
      >
        <svg class="mail-sync-icon" :class="{ spinning: mailSyncing }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
        {{ mailSyncing ? '收取中...' : '刷新邮箱简历' }}
      </button>
    </div>

    <!-- ===== Tab 1: 简历储备库（外部） ===== -->
    <div class="tab-panel" :class="{ active: activeTab === 'external' }">
      <div class="filter-bar">
        <input type="text" placeholder="姓名 / 手机号 / 技能关键字..." id="extSearch" v-model="extFilters.search" @input="renderExt">
        <select id="extStatus" v-model="extFilters.status" @change="renderExt"><option value="all">全部状态</option><option value="available">可联系</option><option value="locked">面试中(锁定)</option><option value="reserve">储备</option><option value="archived">已封存</option></select>
        <select id="extSource" v-model="extFilters.source" @change="renderExt"><option value="all">全部来源</option><option value="mail">邮箱采集</option><option value="boss">Boss直聘</option><option value="liepin">猎聘</option><option value="refer">内推</option><option value="upload">手动上传</option></select>
        <select id="extSkill" v-model="extFilters.skill" @change="renderExt"><option value="all">全部技能</option><option>Java</option><option>K8s</option><option>React</option><option>Vue</option><option>Python</option><option>SQL</option><option>Go</option></select>
        <select id="extEdu" v-model="extFilters.edu" @change="renderExt"><option value="all">全部学历</option><option>大专</option><option>本科</option><option>硕士</option><option>博士</option></select>
        <select id="extYears" v-model="extFilters.years" @change="renderExt"><option value="all">全部年限</option><option value="fresh">应届</option><option value="1-3">1-3年</option><option value="3-5">3-5年</option><option value="5+">5年+</option></select>
        <select id="extProfile" v-model="extFilters.profile" @change="renderExt"><option value="0">画像分不限</option><option value="80">&ge;80</option><option value="60">&ge;60</option></select>
        <select id="extNote" v-model="extFilters.note" @change="renderExt"><option value="all">备注不限</option><option value="yes">有备注</option><option value="no">无备注</option></select>
        <select id="extSort" v-model="extFilters.sort" @change="renderExt"><option value="default">默认排序</option><option value="profile_desc">画像分从高到低</option><option value="time_desc">入库时间最新</option></select>
        <span style="flex:1"></span>
        <span style="font-size:11px;color:var(--c-sub)" id="extCount">共 {{ extFiltered.length }} 人</span>
      </div>

      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;font-size:12px;color:var(--c-sub)">
        <span>已勾选 <b style="color:var(--c-primary)" id="checkedCount">{{ checkedExtCount }}</b> 人</span>
      </div>

      <div class="table-wrap data-region">
        <DataLoadingOverlay :visible="talentLoading" />
        <table v-if="extFiltered.length > 0"><thead><tr>
          <th style="width:34px"><input type="checkbox" id="checkAllExt" @change="toggleAllExt"></th>
          <th>编号</th><th>姓名</th><th>画像</th><th>学历</th><th>年限</th><th>核心技能</th><th>最近公司</th><th>应聘岗位/匹配</th><th>来源</th><th>入库</th><th>状态</th><th>备注</th><th>操作</th>
        </tr></thead><tbody>
          <tr v-for="c in extFiltered" :key="c.id" :class="rowClass(c)">
            <td><input type="checkbox" class="ext-check" v-model="checkedExt[c.id]" :disabled="c.locked" @change="onCheckExt"></td>
            <td>{{ c.id }}</td>
            <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)" @click="openCandidateDrawer(c.name)">{{ c.name }}</a></td>
            <td><span class="portrait-score" :class="c.portraitClass">{{ c.portrait }}</span></td>
            <td>{{ c.edu }}</td><td>{{ c.years }}</td>
            <td v-html="wrapSkills(c.skillsHtml)"></td>
            <td>{{ c.company }}</td>
            <td>
              <template v-if="c.linkedDemands && c.linkedDemands.length">
                <div v-for="ld in c.linkedDemands" :key="ld.demandNo" style="font-size:11px;line-height:1.6">
                  <span style="font-weight:600">{{ ld.position }}</span>
                  <span v-if="ld.matchScore != null" class="portrait-score" :class="ld.matchScore >= 80 ? 'score-high' : (ld.matchScore >= 60 ? 'score-mid' : 'score-low')" style="margin-left:4px">{{ ld.matchScore }}</span>
                </div>
              </template>
              <span v-else-if="c.targetPosition" style="font-size:11px;color:var(--c-sub)">{{ c.targetPosition }}</span>
              <span v-else style="color:var(--c-sub)">—</span>
            </td>
            <td>{{ c.source }}</td><td>{{ c.inDate }}</td>
            <td><StatusBadge :type="c.status === 'available' ? 'done' : (c.status === 'locked' ? 'progress' : 'draft')">{{ c.statusLabel }}</StatusBadge></td>
            <td>
              <template v-if="c.note">
                <span @click.stop="openNote(c.id, c.name)" style="display:inline-block;max-width:90px;padding:2px 8px;background:#FFF8E1;color:#B45309;border-radius:10px;font-size:11px;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="c.note">{{ c.note }}</span>
              </template>
              <template v-else>
                <button class="btn btn-ghost btn-sm" @click.stop="openNote(c.id, c.name)" style="font-size:11px;color:var(--c-sub);padding:2px 8px">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:-1px"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg> 备注
                </button>
              </template>
            </td>
            <td style="white-space:nowrap">
              <template v-if="c.status === 'archived'">
                <span style="font-size:11px;color:var(--c-sub)">超24个月</span>
              </template>
              <template v-else>
                <button class="btn btn-outline btn-sm" @click="openCandidateDrawer(c.name)">查看</button>
                <template v-if="c.locked">
                  <span style="font-size:11px;color:var(--c-sub)"> 已锁定</span>
                </template>
                <template v-else>
                  <button class="btn btn-outline btn-sm" @click="openContactModal(c.name, c.id)"> 联系</button>
                </template>
              </template>
            </td>
          </tr>
        </tbody></table>
        <EmptyState
          v-else-if="!talentLoading"
          title="暂无外部候选人"
          description="人才库暂无符合条件的候选人数据，可通过上传简历或从招聘渠道导入"
          action-label="+ 上传简历"
          @action="uploadResume"
        />
        <div class="table-count" id="extTableCount">共 {{ extFiltered.length }} 条数据</div>
      </div>

      <!-- Batch bar -->
      <div class="batch-bar" id="batchBarExt" :style="{ display: checkedExtCount > 0 ? 'flex' : 'none' }">
        <span>已选择 <span class="count" id="batchCountExt">{{ checkedExtCount }}</span> 位候选人</span>
        <div style="display:flex;gap:8px;align-items:center">
          <button class="btn btn-primary btn-sm" id="addToDemandBtn" @click="openDemandModal">加入需求</button>
          <button class="btn btn-outline btn-sm" @click="batchContact">批量联系</button>
          <button class="btn btn-ghost btn-sm" @click="clearSelectionExt">清除选择</button>
        </div>
      </div>

      <BasePagination
        v-model:current-page="extPage"
        v-model:page-size="extPageSize"
        :total-pages="extTotalPages"
        :total="extTotal"
      />
    </div>

    <!-- ===== Tab 2: 内部员工库 ===== -->
    <div class="tab-panel" :class="{ active: activeTab === 'internal' }">
      <div class="filter-bar">
        <input type="text" v-model="intFilters.search" placeholder="搜索姓名 / 工号 / 技能...">
        <select v-model="intFilters.dept"><option value="all">全部部门</option><option>技术部</option><option>产品部</option><option>数据部</option></select>
        <select v-model="intFilters.sort"><option value="default">综合评估排序</option><option value="years">工龄排序</option><option value="perf">绩效排序</option><option value="match">最近匹配分排序</option></select>
        <button class="btn btn-primary btn-sm" @click="showMatchModal = true">内部匹配</button>
      </div>
      <div class="table-wrap data-region">
        <DataLoadingOverlay :visible="talentLoading" />
        <table v-if="INT_DATA_SOURCE.length > 0"><thead><tr><th style="width:34px"><input type="checkbox" id="checkAllInt" @change="toggleAllInt"></th><th>工号</th><th>姓名</th><th>综合评估</th><th>部门</th><th>岗位</th><th>工龄</th><th>绩效</th><th>最近匹配</th><th>技能标签</th><th>可调岗</th><th>备注</th><th>操作</th></tr></thead><tbody>
          <tr v-for="e in INT_DATA_SOURCE" :key="e.id">
            <td><input type="checkbox" class="int-check" v-model="checkedInt[e.id]" @change="onCheckInt"></td>
            <td>{{ e.id }}</td>
            <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)" @click="openEmployeeDrawer(e.name)">{{ e.name }}</a></td>
            <td v-html="e.scoreHtml"></td>
            <td>{{ e.dept }}</td><td>{{ e.pos }}</td><td>{{ e.years }}</td>
            <td><span style="color:var(--c-done);font-weight:700">{{ e.perf }}</span></td>
            <td v-html="e.matchHtml"></td><td v-html="e.tagsHtml"></td>
            <td><StatusBadge :type="e.transfer ? 'done' : 'warn'">{{ e.transfer ? '可调' : '不可调' }}</StatusBadge></td>
            <td>
              <template v-if="e.note">
                <span @click.stop="openIntNote(e.id, e.name)" style="display:inline-block;max-width:90px;padding:2px 8px;background:#FFF8E1;color:#B45309;border-radius:10px;font-size:11px;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="e.note">{{ e.note }}</span>
              </template>
              <template v-else>
                <button class="btn btn-ghost btn-sm" @click.stop="openIntNote(e.id, e.name)" style="font-size:11px;color:var(--c-sub);padding:2px 8px">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:-1px"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg> 备注
                </button>
              </template>
            </td>
            <td style="white-space:nowrap"><button class="btn btn-outline btn-sm" @click="openEmployeeDrawer(e.name)">查看</button></td>
          </tr>
        </tbody></table>
        <EmptyState
          v-else-if="!talentLoading"
          title="暂无内部员工数据"
          description="内部员工库暂无数据"
        />
        <div class="table-count" id="intTableCount">共 {{ INT_DATA_SOURCE.length }} 条数据</div>
      </div>
      <!-- Batch bar internal -->
      <div class="batch-bar" id="batchBarInt" :style="{ display: checkedIntCount > 0 ? 'flex' : 'none' }">
        <span>已选择 <span class="count" id="batchCountInt">{{ checkedIntCount }}</span> 位员工</span>
        <div style="display:flex;gap:8px">
          <button class="btn btn-primary btn-sm" @click="toast.info('请选择目标岗位后关联内部员工')">加入需求</button>
          <button class="btn btn-ghost btn-sm" @click="clearSelectionInt">清除选择</button>
        </div>
      </div>
    </div>

    <!-- ===== Tab 3: 黑名单 ===== -->
    <div class="tab-panel" :class="{ active: activeTab === 'blacklist' }">
      <div class="filter-bar">
        <input type="text" placeholder="搜索姓名 / 手机号...">
        <select><option>全部原因</option><option>简历造假</option><option>面试严重违纪</option><option>Offer 拒后恶意行为</option></select>
        <button class="btn btn-outline btn-sm">+ 手动加入黑名单</button>
      </div>
      <div class="table-wrap data-region">
        <DataLoadingOverlay :visible="talentLoading" />
        <table v-if="BLACKLIST_DATA_SOURCE.length > 0"><thead><tr><th>候选人</th><th>手机</th><th>加入时间</th><th>原因</th><th>操作人</th><th>到期</th><th>操作</th></tr></thead><tbody>
          <tr v-for="(b, i) in BLACKLIST_DATA_SOURCE" :key="i">
            <td style="color:var(--c-reject);font-weight:600">{{ b.name }}</td>
            <td>{{ b.phone }}</td><td>{{ b.date }}</td><td>{{ b.reason }}</td><td>{{ b.operator }}</td><td>{{ b.expiry }}</td>
            <td><button class="btn btn-text btn-sm">详情</button> <button class="btn btn-text-danger btn-sm">移除</button></td>
          </tr>
        </tbody></table>
        <EmptyState
          v-else-if="!talentLoading"
          title="暂无黑名单数据"
          description="黑名单列表为空"
        />
        <div class="table-count">共 {{ BLACKLIST_DATA_SOURCE.length }} 条数据</div>
      </div>
    </div>

    <!-- Note Modal -->
    <Teleport to="body">
      <div id="noteModal" class="modal-overlay" :class="{ open: showNoteModal }" v-if="showNoteModal" @click.self="closeNoteModal">
        <div class="modal-box" style="width:400px">
          <h3>
            <svg viewBox="0 0 24 24" style="width:18px;height:18px;vertical-align:-2px;stroke:var(--c-primary);fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            备注 &middot; <span id="noteTarget">{{ noteTarget }}</span>
          </h3>
          <textarea id="noteText" v-model="noteText" style="width:100%;min-height:80px;padding:10px;border:1px solid var(--c-border);border-radius:6px;font-size:13px;resize:vertical;box-sizing:border-box" placeholder="添加备注，如：ACE框架经验、沟通能力突出..."></textarea>
          <div class="modal-actions" style="margin-top:12px">
            <button class="btn btn-ghost btn-sm" @click="closeNoteModal">取消</button>
            <button class="btn btn-primary btn-sm" @click="saveNote">保存</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Match Modal -->
    <Teleport to="body">
      <div id="matchModal" class="modal-overlay" v-if="showMatchModal" @click.self="closeMatchModal">
        <div class="modal-box" style="width:640px">
          <h3>内部人才匹配</h3>
          <div style="font-size:13px;color:var(--c-sub);margin-bottom:16px">选择一个正在招聘的岗位，系统自动对内部员工库进行技能+经验+绩效综合匹配打分</div>
          <div style="display:flex;gap:10px;align-items:center;margin-bottom:16px">
            <label style="font-size:13px;font-weight:600;white-space:nowrap">目标岗位</label>
            <select id="matchPosition" v-model="matchPosition" style="flex:1;padding:8px 12px;border:1px solid var(--c-border);border-radius:6px;font-size:13px">
              <option value="">请选择岗位...</option>
              <option value="java">高级Java工程师（架构方向）&middot; 技术部 &middot; 招聘中</option>
              <option value="frontend">前端工程师 &middot; 技术部 &middot; 招聘中</option>
              <option value="pm">产品经理 &middot; 产品部 &middot; 审批中</option>
              <option value="data">数据分析师 &middot; 数据部 &middot; 草稿</option>
            </select>
            <button class="btn btn-primary btn-sm" @click="runMatch">开始匹配</button>
          </div>
          <div id="matchResult" v-if="matchResults.length >= 0" :style="{ display: 'block' }">
            <div style="font-size:12px;color:var(--c-sub);margin-bottom:10px" id="matchSummary">{{ matchSummary }}</div>
            <table style="font-size:13px"><thead><tr><th>工号</th><th>姓名</th><th>部门</th><th>当前岗位</th><th>绩效</th><th>匹配分</th><th>可调岗</th><th>操作</th></tr></thead><tbody id="matchTableBody">
              <tr v-if="matchResults.length === 0"><td colspan="8" style="text-align:center;color:var(--c-sub);padding:24px 0">暂无匹配</td></tr>
              <tr v-for="r in matchResults" :key="r.id">
                <td>{{ r.id }}</td>
                <td><a href="javascript:void(0)" style="font-weight:600;color:var(--c-primary)" @click="openEmployeeDrawer(r.name)">{{ r.name }}</a></td>
                <td>{{ r.dept }}</td><td>{{ r.curPos }}</td>
                <td><span style="color:var(--c-done);font-weight:700">{{ r.perf }}</span></td>
                <td :style="{fontWeight:'700', color: r.score >= 80 ? 'var(--c-done)' : (r.score >= 60 ? 'var(--c-warn)' : 'var(--c-sub)')}">{{ r.score }}</td>
                <td><StatusBadge :type="r.transferable ? 'done' : 'warn'">{{ r.transferable ? '可调' : '不可调' }}</StatusBadge></td>
                <td>
                  <button v-if="r.transferable" class="btn btn-success btn-sm" @click="startInternalInterview(r)">发起面试</button>
                  <span v-else style="font-size:11px;color:var(--c-sub)">不满足条件</span>
                </td>
              </tr>
            </tbody></table>
          </div>
          <div class="modal-actions" style="margin-top:16px">
            <button class="btn btn-ghost btn-sm" @click="closeMatchModal">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Candidate Drawer -->
    <CandidateDrawer
      :visible="showCandidateDrawer"
      :candidate-id="activeCandidateId"
      @close="showCandidateDrawer = false"
      @contact="(data) => { showCandidateDrawer = false; openContactModal(data.name, data.id); }"
      @join="onCandidateJoin"
    />

    <!-- Employee Drawer -->
    <EmployeeDrawer
      :visible="showEmployeeDrawer"
      :employee-id="activeEmployeeId"
      @close="showEmployeeDrawer = false"
      @interview="(data) => toast.info('发起内部面试')"
    />

    <!-- Contact Modal (single & batch) -->
    <ContactModal
      :visible="showContactModal"
      :candidate-name="contactCandidateName"
      :candidate-id="contactCandidateId"
      :selected="contactBatchNames"
      :is-batch="contactIsBatch"
      @close="showContactModal = false"
      @done="onContactDone"
    />

    <!-- 加入需求：选择目标岗位弹窗（优先展示候选人投递的岗位） -->
    <Teleport to="body">
      <div id="demandModal" class="modal-overlay" v-if="showDemandModal" @click.self="showDemandModal = false">
        <div class="modal-box" style="width:520px">
          <h3>加入需求 &middot; 选择目标岗位</h3>
          <div style="font-size:13px;color:var(--c-sub);margin-bottom:12px">
            已选择 <b style="color:var(--c-primary)">{{ selectedCandidates.length }}</b> 位候选人：
            <span v-for="c in selectedCandidates" :key="c.id" class="dm-cand-chip">
              {{ c.name }}<template v-if="c.targetPosition">（投递：{{ c.targetPosition }}）</template>
            </span>
          </div>
          <div v-if="demandOptionViews.length" class="dm-list">
            <div
              v-for="d in demandOptionViews" :key="d.id"
              class="dm-option" :class="{ active: selectedDemandId === d.id }"
              @click="selectedDemandId = d.id"
            >
              <div style="flex:1;min-width:0">
                <span style="font-weight:600;color:var(--c-text)">{{ d.position }}</span>
                <span style="color:var(--c-sub);font-size:12px"> &middot; {{ d.dept }} &middot; {{ d.statusLabel }}</span>
                <span v-if="d.recommended" class="dm-badge dm-badge-rec">投递岗位</span>
                <span v-if="d.partiallyLinked" class="dm-badge dm-badge-linked">部分人已在流程</span>
              </div>
              <span style="font-size:12px;color:var(--c-sub);white-space:nowrap">已有 {{ d.linkedCount || 0 }} 人</span>
            </div>
          </div>
          <div v-else style="padding:24px 0;text-align:center;color:var(--c-sub);font-size:13px">
            暂无招聘中/审批中的需求，请先到「需求管理」新建
          </div>
          <div class="modal-actions" style="margin-top:16px">
            <button class="btn btn-ghost btn-sm" @click="showDemandModal = false">取消</button>
            <button class="btn btn-primary btn-sm" :disabled="!selectedDemandId" @click="confirmAddToDemand">确认加入</button>
          </div>
        </div>
      </div>
    </Teleport>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import { fetchTalent, updateTalentNote, fetchMatchResults, uploadResumeFile } from '../api/talent.js';
import { fetchDemands, linkCandidateToDemand } from '../api/demand.js';
import { useToast } from '../composables/useToast.js';
import { useAppError } from '../composables/useAppError.js';
import CandidateDrawer from '../components/CandidateDrawer.vue';
import EmployeeDrawer from '../components/EmployeeDrawer.vue';
import ContactModal from '../components/ContactModal.vue';
import EmptyState from '../components/EmptyState.vue';
import DataLoadingOverlay from '../components/DataLoadingOverlay.vue';
import BasePagination from '../components/BasePagination.vue';

const { toast } = useToast();
const { handleError } = useAppError();

const showReminder = ref(false);
const showNoteModal = ref(false);
const showMatchModal = ref(false);
const activeTab = ref('external');
const noteTarget = ref('');
const noteText = ref('');
const currentNoteId = ref(null);
const currentNoteType = ref('ext');
const matchPosition = ref('');
const matchResults = ref([]);
const matchSummary = ref('');

// API data refs
const apiExtData = ref(null);
const apiIntData = ref(null);
const apiBlacklistData = ref(null);
const talentLoading = ref(true);
const talentLoadError = ref('');

// 「加入需求」弹窗：需求列表来自真实 API；按候选人投递岗位优先排序
const demandOptions = ref([]);
const demandOptionsLoaded = ref(false);
const showDemandModal = ref(false);
const selectedDemandId = ref('');

// 当前勾选的候选人（含投递岗位与已关联需求，用于推荐与标记）
const selectedCandidates = computed(() =>
  Object.keys(checkedExt)
    .filter(k => checkedExt[k])
    .map(id => EXT_DATA_SOURCE.value.find(x => x.id === id))
    .filter(Boolean)
    .map(c => ({
      id: c.id,
      name: c.name,
      targetPosition: c.targetPosition || '',
      linkedDemands: Array.isArray(c.linkedDemands) ? c.linkedDemands : [],
    }))
);

const demandOptionViews = computed(() => {
  const targets = selectedCandidates.value
    .map(c => (c.targetPosition || '').trim())
    .filter(Boolean);
  const linkedNos = new Set(
    selectedCandidates.value.flatMap(c => c.linkedDemands.map(ld => ld.demandNo))
  );
  return demandOptions.value
    .map(d => ({
      ...d,
      recommended: targets.some(t => t && (d.position || '').includes(t.slice(0, 4)) || t.includes((d.position || '').slice(0, 4))),
      partiallyLinked: linkedNos.has(d.id),
    }))
    .sort((a, b) => (b.recommended - a.recommended) || ((a.linkedCount || 0) - (b.linkedCount || 0)));
});

function openDemandModal() {
  selectedDemandId.value = '';
  if (!demandOptionsLoaded.value) loadDemandOptions();
  showDemandModal.value = true;
}

function confirmAddToDemand() {
  const d = demandOptionViews.value.find(x => x.id === selectedDemandId.value);
  if (!d) return;
  addToDemand(d.id, d.position);
}

async function loadDemandOptions() {
  try {
    const { data } = await fetchDemands({ pageSize: 100 });
    const list = Array.isArray(data) ? data : [];
    // 优先展示招聘中/审批中的需求，其余状态排后
    const rank = { open: 0, approval: 1 };
    demandOptions.value = list
      .filter(d => d.status === 'open' || d.status === 'approval')
      .sort((a, b) => (rank[a.status] ?? 9) - (rank[b.status] ?? 9));
  } catch (e) {
    console.warn('[RecruitTalent] 需求列表加载失败:', e);
    demandOptions.value = [];
  } finally {
    demandOptionsLoaded.value = true;
  }
}

const EXT_DATA_SOURCE = computed(() => apiExtData.value ?? []);
const INT_DATA_SOURCE = computed(() => apiIntData.value ?? []);
const BLACKLIST_DATA_SOURCE = computed(() => apiBlacklistData.value ?? []);

// Pagination
const extPage = ref(1);
const extPageSize = ref(20);
const extTotal = ref(0);
const extTotalPages = computed(() => Math.max(1, Math.ceil(extTotal.value / extPageSize.value)));

// Internal tab reactive filters
const intFilters = reactive({ search: '', dept: 'all', sort: 'default' });

async function loadFromApi() {
  talentLoading.value = true;
  talentLoadError.value = '';
  try {
    const talentData = await fetchTalent({
      page: extPage.value,
      pageSize: extPageSize.value,
      sort: extFilters.sort,
    });
    if (talentData) {
      const ext = talentData.ext ?? talentData.external ?? null;
      apiExtData.value = Array.isArray(ext) ? ext.map(c => ({
        ...c,
        skillsHtml: c.skillsHtml
          || (Array.isArray(c.skills) ? c.skills.map(s => `<span class="tag-item tag-hit">${s}</span>`).join('') : (c.skills || '')),
      })) : null;
      apiIntData.value = Array.isArray(talentData.int ?? talentData.internal) ? (talentData.int ?? talentData.internal) : [];
      apiBlacklistData.value = Array.isArray(talentData.blacklist) ? talentData.blacklist : [];
      extTotal.value = talentData.total ?? 0;
    }
  } catch (e) {
    talentLoadError.value = e.message || '人才库数据加载失败';
    apiExtData.value = [];
    apiIntData.value = [];
    apiBlacklistData.value = [];
    console.warn('Failed to load talent data from API:', e);
  } finally {
    talentLoading.value = false;
  }
}

// 手动刷新邮箱简历
const mailSyncing = ref(false);
async function manualMailSync() {
  if (mailSyncing.value) return;
  mailSyncing.value = true;
  try {
    const { syncAllEmailAccounts } = await import('../api/config.js');
    const res = await syncAllEmailAccounts();
    const r = (res && res.data) ? res.data : res;
    if (r && r.accepted) {
      toast.success(r.message || '同步已开始，请稍后刷新查看结果');
      setTimeout(() => { loadFromApi(); }, 8000);
    } else if (r && r.accounts_checked === 0) {
      toast.warning('没有已启用的收简历邮箱，请先在「基础配置」中添加');
    } else if (r && (r.new_emails > 0 || r.resumes_ingested > 0)) {
      toast.success(`收取完成：新邮件 ${r.new_emails} 封，新入库简历 ${r.resumes_ingested} 份`);
      await loadFromApi();
    } else if (r && r.status === 'partial') {
      toast.warning('部分邮箱同步失败，请检查邮箱配置');
      await loadFromApi();
    } else {
      toast.success('收取完成：暂无新简历邮件');
    }
  } catch (e) {
    toast.error('邮箱刷新失败: ' + e.message);
  } finally {
    mailSyncing.value = false;
  }
}

// 分页变化时重新加载
function onExtPageChange() { loadFromApi(); }
watch([extPage, extPageSize], onExtPageChange);

const tabs = [
  { id: 'external', label: '简历储备库（外部）' },
  { id: 'internal', label: '内部员工库' },
  { id: 'blacklist', label: '黑名单' }
];

// External filters
const extFilters = reactive({
  search: '', status: 'all', source: 'all', skill: 'all',
  edu: 'all', years: 'all', profile: '0', note: 'all', sort: 'default'
});

watch(() => extFilters.sort, () => {
  extPage.value = 1;
  loadFromApi();
});

const checkedExt = reactive({});
const checkedInt = reactive({});

const checkedExtCount = computed(() => Object.keys(checkedExt).filter(k => checkedExt[k]).length);
const checkedIntCount = computed(() => Object.keys(checkedInt).filter(k => checkedInt[k]).length);

function getProfileScore(c) {
  const direct = Number(c.profileScore ?? c.staticAbilityScore ?? c.static_ability_score);
  if (Number.isFinite(direct) && direct > 0) return direct;
  const match = String(c.portrait || '').match(/\d+/);
  return match ? Number(match[0]) : 0;
}

function getDateValue(c) {
  const raw = String(c.storageTime || c.inDate || c.createdAt || '');
  const value = Date.parse(raw.includes('-') && raw.length <= 5 ? `2026-${raw}` : raw);
  return Number.isFinite(value) ? value : 0;
}

const extFiltered = computed(() => {
  let list = EXT_DATA_SOURCE.value.filter(c => {
    if (extFilters.status !== 'all' && c.status !== extFilters.status) return false;
    if (extFilters.source !== 'all') {
      const m = { mail: '邮箱', boss: 'Boss', liepin: '猎聘', refer: '内推', upload: '手动上传' };
      if (c.source !== m[extFilters.source]) return false;
    }
    if (extFilters.skill !== 'all' && c.skillsHtml.indexOf(extFilters.skill) < 0) return false;
    if (extFilters.edu !== 'all' && c.edu !== extFilters.edu) return false;
    if (extFilters.years !== 'all') {
      const y = parseInt(c.years) || 0;
      if (extFilters.years === 'fresh' && y > 0) return false;
      if (extFilters.years === '1-3' && (y < 1 || y > 3)) return false;
      if (extFilters.years === '3-5' && (y < 3 || y > 5)) return false;
      if (extFilters.years === '5+' && y < 5) return false;
    }
    const profileVal = parseInt(extFilters.profile) || 0;
    if (profileVal > 0 && getProfileScore(c) < profileVal) return false;
    if (extFilters.note === 'yes' && !c.note) return false;
    if (extFilters.note === 'no' && c.note) return false;
    if (extFilters.search) {
      const kw = extFilters.search.toLowerCase();
      if (String(c.name || '').toLowerCase().indexOf(kw) < 0 && String(c.company || '').toLowerCase().indexOf(kw) < 0 && String(c.skillsHtml || '').toLowerCase().indexOf(kw) < 0) return false;
    }
    return true;
  });
  if (extFilters.sort === 'profile_desc') list.sort((a, b) => getProfileScore(b) - getProfileScore(a));
  else if (extFilters.sort === 'time_desc') list.sort((a, b) => getDateValue(b) - getDateValue(a));
  return list;
});

function rowClass(c) {
  if (c.locked && c.status === 'locked') return 'row-locked';
  if (c.status === 'archived') return 'row-archived';
  return '';
}

function toggleAllExt(e) {
  extFiltered.value.forEach(c => {
    if (!c.locked) checkedExt[c.id] = e.target.checked;
  });
}
function onCheckExt() {}
function clearSelectionExt() {
  Object.keys(checkedExt).forEach(k => delete checkedExt[k]);
  try { document.getElementById('checkAllExt').checked = false; } catch(e) {}
}
function toggleAllInt(e) {
  INT_DATA_SOURCE.value.forEach(e2 => { checkedInt[e2.id] = e.target.checked; });
}
function onCheckInt() {}
function clearSelectionInt() {
  Object.keys(checkedInt).forEach(k => delete checkedInt[k]);
}

function renderExt() {}

// Note modal
function openNote(id, name) {
  currentNoteId.value = id; currentNoteType.value = 'ext';
  noteTarget.value = name;
  const c = EXT_DATA_SOURCE.value.find(x => x.id === id);
  noteText.value = c ? c.note : '';
  showNoteModal.value = true;
}
function openIntNote(id, name) {
  currentNoteId.value = id; currentNoteType.value = 'int';
  noteTarget.value = name;
  const e = INT_DATA_SOURCE.value.find(x => x.id === id);
  noteText.value = e ? e.note : '';
  showNoteModal.value = true;
}
function closeNoteModal() { showNoteModal.value = false; currentNoteId.value = null; }
async function saveNote() {
  if (!currentNoteId.value) return;
  const text = noteText.value.trim();
  try {
    await updateTalentNote(currentNoteId.value, text);
  } catch (e) {
    console.warn('[RecruitTalent] updateTalentNote failed, using local fallback:', e);
  }
  if (currentNoteType.value === 'ext') {
    const c = EXT_DATA_SOURCE.value.find(x => x.id === currentNoteId.value);
    if (c) c.note = text;
  } else {
    const e = INT_DATA_SOURCE.value.find(x => x.id === currentNoteId.value);
    if (e) e.note = text;
  }
  toast.success('备注已保存');
  closeNoteModal();
}

// Match modal
function closeMatchModal() { showMatchModal.value = false; matchResults.value = []; }
async function runMatch() {
  if (!matchPosition.value) { toast.warning('请先选择一个目标岗位'); return; }
  const posNames = { java: '高级Java工程师（架构方向）', frontend: '前端工程师', pm: '产品经理', data: '数据分析师' };
  const posName = posNames[matchPosition.value] || matchPosition.value;

  try {
    const resp = await fetchMatchResults(matchPosition.value);
    if (resp && resp.results) {
      matchResults.value = resp.results;
    } else {
      matchResults.value = [];
    }
  } catch (e) {
    console.warn('[RecruitTalent] fetchMatchResults failed:', e);
    matchResults.value = [];
  }
  matchSummary.value = '匹配岗位：' + posName + ' · 匹配 ' + matchResults.value.length + ' 人（基于真实数据运算）';
}

// Candidate/Employee Drawer state
const showCandidateDrawer = ref(false);
const activeCandidateId = ref('');
const activeCandidateName = ref('');
function openCandidateDrawer(name) {
  const c = EXT_DATA_SOURCE.value.find(x => x.name === name);
  activeCandidateId.value = c?.id || name;
  activeCandidateName.value = name;
  showCandidateDrawer.value = true;
}
const showEmployeeDrawer = ref(false);
const activeEmployeeId = ref('');
const activeEmployeeName = ref('');
function openEmployeeDrawer(name) {
  const e = INT_DATA_SOURCE.value.find(x => x.name === name);
  activeEmployeeId.value = e?.id || name;
  activeEmployeeName.value = name;
  showEmployeeDrawer.value = true;
}

// Contact Modal state
const showContactModal = ref(false);
const contactCandidateId = ref('');
const contactCandidateName = ref('');
const contactBatchNames = ref([]);
const contactIsBatch = ref(false);
function openContactModal(name, id) {
  contactCandidateId.value = id || '';
  contactCandidateName.value = name;
  contactBatchNames.value = [];
  contactIsBatch.value = false;
  showContactModal.value = true;
}

// Batch
async function addToDemand(demandId, demandName) {
  const checkedIds = Object.keys(checkedExt).filter(k => checkedExt[k]);
  const names = checkedIds.map(id => { const c = EXT_DATA_SOURCE.value.find(x => x.id === id); return c ? c.name : ''; }).filter(Boolean);
  if (names.length === 0) return;

  // 逐个调用真实 link 接口，按返回的 linked 判定成败，汇总提示
  const okNames = [];
  const failReasons = [];
  let latestLinkedCount = null;
  for (const name of names) {
    try {
      const r = await linkCandidateToDemand(demandId, name);
      if (r && r.linked) {
        okNames.push(name);
        if (typeof r.linkedCount === 'number') latestLinkedCount = r.linkedCount;
      } else {
        failReasons.push(name + '：' + ((r && r.reason) || '加入失败'));
      }
    } catch (e) {
      console.warn('[RecruitTalent] linkCandidateToDemand failed for', name, e);
      failReasons.push(name + '：接口异常');
    }
  }

  if (okNames.length) {
    demandOptions.value = demandOptions.value.map(d =>
      d.id === demandId
        ? { ...d, linkedCount: latestLinkedCount ?? ((d.linkedCount || 0) + okNames.length) }
        : d
    );
    const key = 'demand_' + demandId + '_linked';
    const linked = (() => { try { return JSON.parse(localStorage.getItem(key)) || []; } catch(e) { return []; } })();
    okNames.forEach(n => { if (linked.indexOf(n) < 0) linked.push(n); });
    localStorage.setItem(key, JSON.stringify(linked));
    demandOptionsLoaded.value = false;
    await loadDemandOptions();
  }

  showDemandModal.value = false;
  if (failReasons.length === 0) {
    toast.success('已将 ' + okNames.length + ' 位候选人加入需求「' + demandName + '」');
  } else if (okNames.length === 0) {
    toast.error('加入需求「' + demandName + '」失败：' + failReasons.join('；'));
  } else {
    toast.warning('已加入 ' + okNames.length + ' 人，失败 ' + failReasons.length + ' 人（' + failReasons.join('；') + '）');
  }
  clearSelectionExt();
}

function batchContact() {
  const checkedIds = Object.keys(checkedExt).filter(k => checkedExt[k]);
  const names = checkedIds.map(id => { const c = EXT_DATA_SOURCE.value.find(x => x.id === id); return c ? c.name : ''; }).filter(Boolean);
  if (names.length === 0) return;
  contactCandidateId.value = '';
  contactCandidateName.value = '';
  contactBatchNames.value = names;
  contactIsBatch.value = true;
  showContactModal.value = true;
}

function onContactDone(result) {
  showContactModal.value = false;
  if (!result?.fallback) {
    toast.success('联系记录已存档');
  }
}

function onCandidateJoin(data) {
  showCandidateDrawer.value = false;
  // 抽屉里对单个候选人点「加入需求」：自动勾选该候选人并打开岗位选择弹窗
  if (data && data.id) {
    clearSelectionExt();
    checkedExt[data.id] = true;
  }
  openDemandModal();
}

const uploading = ref(false);
const resumeFileInput = ref(null);

function uploadResume() {
  resumeFileInput.value && resumeFileInput.value.click();
}

async function onResumeFilePicked(e) {
  const file = e.target.files && e.target.files[0];
  e.target.value = '';
  if (!file) return;
  uploading.value = true;
  try {
    const r = await uploadResumeFile(file);
    const engine = r.parse_engine === 'deepseek' ? 'DeepSeek' : '本地解析';
    toast.success(`简历已入库：${r.candidate_name}（${r.candidate_no} · ${engine}）`);
    await loadFromApi();
  } catch (err) {
    handleError(err, 'RecruitTalent.uploadResume');
    toast.error('简历解析入库失败：' + (err?.response?.data?.message || err.message || '未知错误'));
  } finally {
    uploading.value = false;
  }
}

async function startInternalInterview(r) {
  try {
    const { createInterview } = await import('../api/interview.js');
    await createInterview({ name: r.name, position: matchPosition.value, type: 'internal' });
    toast.success('已发起内部面试：' + r.name);
  } catch (e) {
    handleError(e, 'RecruitTalent.startInternalInterview');
    toast.error('发起内部面试失败');
  }
}

function wrapSkills(html) { return '<span class="skill-inline">' + html + '</span>'; }
function hoverStyle(e, on) { e.target.style.background = on ? 'var(--c-bg)' : ''; }

// Close dropdowns on external click
function onDocClick(e) {
  const rb = document.getElementById('reminderBtn'), rd = document.getElementById('reminderDropdown');
  if (showReminder.value && rd && rb && !rb.contains(e.target) && !rd.contains(e.target)) showReminder.value = false;
}

onMounted(() => {
  document.addEventListener('click', onDocClick);
  loadFromApi();
  loadDemandOptions();
});
onUnmounted(() => {
  document.removeEventListener('click', onDocClick);
});
</script>

<style scoped>
.batch-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; background: var(--c-primary-subtle);
  border: 1px solid rgba(79,110,247,0.18); border-radius: 8px; font-size: 13px; margin-top: 12px;
}
.batch-bar .count { font-weight: 700; color: var(--c-primary); }
.data-region { position: relative; min-height: 220px; }
/* ── 加入需求弹窗 ── */
.dm-cand-chip { display: inline-block; padding: 2px 8px; margin: 2px 4px 2px 0; background: var(--c-primary-subtle); border-radius: 10px; font-size: 12px; color: var(--c-text); }
.dm-list { max-height: 320px; overflow-y: auto; border: 1px solid var(--c-border); border-radius: 8px; }
.dm-option { display: flex; align-items: center; gap: 8px; padding: 10px 14px; cursor: pointer; border-bottom: 1px solid var(--c-border); font-size: 13px; transition: background 0.15s; }
.dm-option:last-child { border-bottom: none; }
.dm-option:hover { background: var(--c-bg); }
.dm-option.active { background: var(--c-primary-subtle); box-shadow: inset 2px 0 0 var(--c-primary); }
.dm-badge { display: inline-block; margin-left: 6px; padding: 1px 7px; border-radius: 8px; font-size: 11px; font-weight: 600; }
.dm-badge-rec { background: var(--c-primary-subtle); color: var(--c-primary); }
.dm-badge-linked { background: #FFF8E1; color: #B45309; }
.row-locked { opacity: 0.7; }
.row-archived { opacity: 0.4; }
.mail-sync-btn { margin-left: auto; display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; }
.mail-sync-icon { width: 13px; height: 13px; flex-shrink: 0; }
.mail-sync-icon.spinning { animation: mail-sync-spin 1s linear infinite; }
@keyframes mail-sync-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) {
  .mail-sync-icon.spinning { animation: none; }
}

</style>
