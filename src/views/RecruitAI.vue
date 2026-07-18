<template>
  <WorkbenchLayout title="招聘辅助中心" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <div class="permission-bar">
      本页面仅<b>系统管理员</b>可见 · 集中所有<b>用户主动发起</b>的招聘辅助能力 · 流程内嵌的简历解析、匹配评分、联系话术辅助已嵌入各业务页面
    </div>

    <!-- 6 Tab navigation -->
    <div class="tabs" role="tablist" aria-label="招聘辅助功能">
      <button v-for="tab in tabs" :key="tab.id" class="tab"
        :id="'tab-btn-' + tab.id"
        :class="{ active: activeTab === tab.id }"
        :tabindex="activeTab === tab.id ? 0 : -1"
        :aria-selected="activeTab === tab.id ? 'true' : 'false'"
        role="tab" @click="activeTab = tab.id"
        @keydown.left.prevent="focusPrevTab"
        @keydown.right.prevent="focusNextTab"
        @keydown.home.prevent="activeTab = tabs[0].id"
        @keydown.end.prevent="activeTab = tabs[tabs.length - 1].id"
      >{{ tab.number }} {{ tab.title }}</button>
    </div>

    <!-- ============ Tab 1: JD 草稿生成 ============ -->
    <div class="tab-panel" :class="{ active: activeTab === 'jd' }" role="tabpanel" aria-labelledby="tab-btn-jd" aria-label="JD 草稿生成">
      <div data-slot="ai-workspace">
        <!-- Conversation area -->
        <div data-slot="ai-conversation">
          <!-- AI intro -->
          <AiChatMessage role="ai" status="complete">
            <p>输入岗位信息，我帮你生成结构化的 JD 草稿，包括岗位职责、必备技能、加分项和任职资格。所有内容需人工审核确认后使用。</p>
          </AiChatMessage>

          <!-- Loading -->
          <AiChatMessage v-if="jdLoading" role="ai" status="loading" />

          <!-- Error -->
          <AiChatMessage v-if="jdError && !jdLoading" role="ai" status="error">
            <template #error>{{ jdError }}</template>
          </AiChatMessage>

          <!-- Result -->
          <template v-if="jdResult && !jdLoading">
            <AiChatMessage role="ai" status="complete">
              <div data-slot="ai-jd-result">
                <div data-slot="ai-jd-header">
                  <h4>{{ jdResult.position }}</h4>
                  <span data-slot="ai-jd-dept">{{ jdResult.department }}</span>
                </div>
                <div data-slot="ai-jd-section">
                  <div data-slot="ai-jd-section-title">岗位职责</div>
                  <ol data-slot="ai-jd-list">
                    <li v-for="(r, i) in jdResult.responsibilities" :key="'r'+i">{{ r }}</li>
                  </ol>
                </div>
                <div data-slot="ai-jd-section">
                  <div data-slot="ai-jd-section-title">必备技能</div>
                  <div data-slot="ai-jd-skill-table">
                    <div v-for="s in jdResult.required_skills" :key="s.name" data-slot="ai-jd-skill-row">
                      <span data-slot="ai-jd-skill-name">{{ s.name }}</span>
                      <StatusBadge :type="s.weight === '必须' ? 'done' : 'progress'">{{ s.weight }}</StatusBadge>
                      <span data-slot="ai-jd-skill-desc">{{ s.description }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="jdResult.plus_skills?.length" data-slot="ai-jd-section">
                  <div data-slot="ai-jd-section-title">加分项</div>
                  <ul data-slot="ai-jd-list">
                    <li v-for="s in jdResult.plus_skills" :key="s.name">{{ s.name }} — {{ s.description }}</li>
                  </ul>
                </div>
                <div data-slot="ai-jd-section">
                  <div data-slot="ai-jd-section-title">任职资格</div>
                  <div data-slot="ai-jd-info-grid">
                    <div v-for="(v, k) in jdResult.qualifications" :key="k" data-slot="ai-jd-info-item">
                      <span data-slot="ai-jd-info-label">{{ qualLabels[k] || k }}</span>
                      <span data-slot="ai-jd-info-value">{{ v }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </AiChatMessage>
            <AiDisclaimer />
          </template>
        </div>

        <!-- Input area -->
        <div data-slot="ai-input-area">
          <div style="display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap">
            <input v-model="jdForm.position" placeholder="岗位名称 (必填)" style="flex:1;min-width:140px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-text)" aria-label="岗位名称">
            <select v-model="jdForm.department" style="flex:1;min-width:120px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="部门">
              <option value="">部门 (必填)</option>
              <option v-for="d in departments" :key="d" :value="d">{{ d }}</option>
            </select>
            <select v-model="jdForm.level" style="width:100px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="职级">
              <option v-for="lv in levels" :key="lv" :value="lv">{{ lv }}</option>
            </select>
          </div>
          <AiPromptInput
            v-model="jdForm.requirements"
            :status="jdStatus"
            :disabled="!jdForm.position || !jdForm.department"
            placeholder="描述岗位核心要求 (选填)，例如：5年Java经验、大厂背景、熟悉微服务架构..."
            hint=""
            layout="compact"
            aria-label="JD 草稿需求描述"
            @submit="generateJd"
          />
        </div>
      </div>
    </div>

    <!-- ============ Tab 2: 语义简历搜索 ============ -->
    <div class="tab-panel" :class="{ active: activeTab === 'search' }" role="tabpanel" aria-labelledby="tab-btn-search" aria-label="语义简历搜索">
      <div data-slot="ai-workspace">
        <div data-slot="ai-conversation">
          <AiChatMessage role="ai" status="complete">
            <p>用自然语言描述你需要的候选人，系统解析语义后在人才库中搜索匹配简历。不用拼关键词，直接描述即可。</p>
          </AiChatMessage>
          <AiChatMessage v-if="searchLoading" role="ai" status="loading" />
          <AiChatMessage v-if="searchError && !searchLoading" role="ai" status="error">
            <template #error>{{ searchError }}</template>
          </AiChatMessage>
          <template v-if="searchResults.length && !searchLoading">
            <AiChatMessage role="ai" status="complete">
              <div style="margin-bottom:12px;font-weight:600;color:var(--c-text)">找到 {{ searchResults.length }} 位匹配候选人：</div>
              <div class="table-wrap" style="margin-bottom:0">
                <table>
                  <thead><tr><th>姓名</th><th style="text-align:right">画像分</th><th style="text-align:right">匹配度</th><th>匹配理由</th><th>操作</th></tr></thead>
                  <tbody>
                    <tr v-for="r in searchResults" :key="r.id">
                      <td><b>{{ r.name }}</b></td>
                      <td class="num-cell">{{ r.portraitScore }}</td>
                      <td class="num-cell">
                        <span :style="{ color: r.matchScore >= 90 ? 'var(--c-done)' : r.matchScore >= 75 ? 'var(--c-warn)' : 'var(--c-draft)' }">{{ r.matchScore }}</span>
                      </td>
                      <td>
                        <div class="reason-tags"><span v-for="(m, mi) in r.match_reasons" :key="mi" class="reason-tag">{{ m }}</span></div>
                      </td>
                      <td><button class="btn btn-text btn-sm" @click="viewResume(r.id)">查看简历</button></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </AiChatMessage>
          </template>
          <AiChatMessage v-if="!searchLoading && searchResults.length === 0 && searchAttempted" role="ai" status="complete">
            <p style="color:var(--c-sub)">未找到匹配的候选人，尝试调整搜索描述</p>
          </AiChatMessage>
        </div>
        <div data-slot="ai-input-area">
          <AiPromptInput
            v-model="searchQuery"
            :status="searchStatus"
            :disabled="!searchQuery.trim()"
            placeholder="描述你想要的候选人，例如：5年Java 大厂背景 做过微服务架构 熟悉K8s..."
            hint="Enter 快速搜索"
            layout="compact"
            aria-label="搜索描述"
            @submit="searchResume"
          />
        </div>
      </div>
    </div>

    <!-- ============ Tab 3: 人岗匹配 ============ -->
    <div class="tab-panel" :class="{ active: activeTab === 'match' }" role="tabpanel" aria-labelledby="tab-btn-match" aria-label="人岗匹配工作台">
      <div data-slot="ai-workspace">
        <div data-slot="ai-conversation">
          <AiChatMessage role="ai" status="complete">
            <p>选择候选人和岗位，系统会基于简历画像和岗位 JD 进行多维匹配打分，并给出详细理由。</p>
          </AiChatMessage>
          <AiChatMessage v-if="matchLoading" role="ai" status="loading" />
          <AiChatMessage v-if="matchError && !matchLoading" role="ai" status="error">
            <template #error>{{ matchError }}</template>
          </AiChatMessage>
          <template v-if="matchResult && !matchLoading">
            <AiChatMessage role="ai" status="complete">
              <div data-slot="ai-match-result">
                <div data-slot="ai-match-score-row">
                  <div data-slot="ai-match-big-score">
                    <span data-slot="ai-match-score-num">{{ matchResult.overall_score }}</span>
                    <span data-slot="ai-match-score-label">综合匹配得分</span>
                  </div>
                  <div data-slot="ai-match-breakdown">
                    <div data-slot="ai-match-item"><span data-slot="ai-match-item-label">画像分</span><div class="progress-bar"><div class="progress-fill" :style="{ width: matchResult.profile_score + '%' }"></div></div><span data-slot="ai-match-item-val">{{ matchResult.profile_score }}</span></div>
                    <div data-slot="ai-match-item"><span data-slot="ai-match-item-label">匹配分</span><div class="progress-bar"><div class="progress-fill" :style="{ width: matchResult.match_score + '%' }"></div></div><span data-slot="ai-match-item-val">{{ matchResult.match_score }}</span></div>
                    <div data-slot="ai-match-item"><span data-slot="ai-match-item-label">综合等级</span><StatusBadge :type="matchResult.grade === 'A' ? 'done' : matchResult.grade === 'B' ? 'progress' : 'warn'">{{ matchResult.grade }} 级</StatusBadge></div>
                  </div>
                </div>
                <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">匹配优势</div><ul data-slot="ai-jd-list"><li v-for="(s, i) in matchResult.strengths" :key="'s'+i">{{ s }}</li></ul></div>
                <div v-if="matchResult.missing_skills?.length" data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">待补足技能</div><div data-slot="ai-jd-skill-table"><div v-for="ms in matchResult.missing_skills" :key="ms.skill" data-slot="ai-jd-skill-row"><span data-slot="ai-jd-skill-name">{{ ms.skill }}</span><StatusBadge :type="ms.importance === '加分项' ? 'draft' : 'warn'">{{ ms.importance }}</StatusBadge><span data-slot="ai-jd-skill-desc">{{ ms.note }}</span></div></div></div>
                <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">详细理由</div><ul data-slot="ai-jd-list"><li v-for="(r, i) in matchResult.reasons" :key="'mr'+i">{{ r }}</li></ul></div>
              </div>
            </AiChatMessage>
            <AiDisclaimer />
          </template>
        </div>
        <div data-slot="ai-input-area">
          <div style="display:flex;gap:8px;margin-bottom:8px">
            <select v-model="matchForm.candidateId" style="flex:1;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择候选人">
              <option value="">选择候选人</option>
              <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }} — {{ c.title }}</option>
            </select>
            <select v-model="matchForm.demandId" style="flex:1;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择岗位">
              <option value="">选择岗位</option>
              <option v-for="d in demands" :key="d.id" :value="d.id">{{ d.name }} · {{ d.dept }}</option>
            </select>
          </div>
          <button class="btn btn-primary" style="width:100%" :disabled="!matchForm.candidateId || !matchForm.demandId || matchLoading" @click="runMatch">
            <AiSkeleton v-if="matchLoading" variant="spinner" />
            {{ matchLoading ? '匹配中...' : '开始匹配' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ============ Tab 4: 面试辅助 ============ -->
    <div class="tab-panel" :class="{ active: activeTab === 'interview' }" role="tabpanel" aria-labelledby="tab-btn-interview" aria-label="面试辅助">
      <div data-slot="ai-workspace">
        <div data-slot="ai-conversation">
          <AiChatMessage role="ai" status="complete">
            <p>根据候选人简历和岗位 JD，AI 自动生成针对性面试问题。选择上下文信息后开始生成。</p>
          </AiChatMessage>
          <AiChatMessage v-if="interviewLoading" role="ai" status="loading" />
          <AiChatMessage v-if="interviewError && !interviewLoading" role="ai" status="error">
            <template #error>{{ interviewError }}</template>
          </AiChatMessage>
          <template v-if="interviewQuestions.length && !interviewLoading">
            <AiChatMessage role="ai" status="complete">
              <ol data-slot="ai-question-list">
                <li v-for="(q, qi) in interviewQuestions" :key="qi" data-slot="ai-question-item">
                  <div data-slot="ai-question-text">{{ q.question }}</div>
                  <div data-slot="ai-question-meta">
                    <StatusBadge type="progress">{{ q.dimension }}</StatusBadge>
                    <button class="btn btn-text btn-sm" @click="toggleHint(qi)" :aria-expanded="expandedHints[qi] ? 'true' : 'false'">
                      {{ expandedHints[qi] ? '收起提示' : '回答提示' }}
                      <svg viewBox="0 0 24 24" style="width:12px;height:12px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;transition:transform .2s" :style="{ transform: expandedHints[qi] ? 'rotate(180deg)' : '' }"><polyline points="6 9 12 15 18 9"/></svg>
                    </button>
                  </div>
                  <div v-if="expandedHints[qi]" data-slot="ai-question-hint">{{ q.expected_answer_hints }}</div>
                </li>
              </ol>
            </AiChatMessage>
            <AiDisclaimer />
          </template>
        </div>
        <div data-slot="ai-input-area">
          <div style="display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap">
            <select v-model="interviewForm.candidateId" style="flex:1;min-width:130px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择候选人">
              <option value="">选择候选人</option>
              <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <select v-model="interviewForm.demandId" style="flex:1;min-width:130px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择岗位">
              <option value="">选择岗位</option>
              <option v-for="d in demands" :key="d.id" :value="d.id">{{ d.name }}</option>
            </select>
            <select v-model="interviewForm.round" style="width:90px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="面试轮次">
              <option v-for="rnd in interviewRounds" :key="rnd.value" :value="rnd.value">{{ rnd.label }}</option>
            </select>
          </div>
          <button class="btn btn-primary" style="width:100%" :disabled="!interviewForm.candidateId || !interviewForm.demandId || interviewLoading" @click="generateQuestions">
            <AiSkeleton v-if="interviewLoading" variant="spinner" />
            {{ interviewLoading ? '生成中...' : '生成面试问题' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ============ Tab 5: 招聘深度报表 ============ -->
    <div class="tab-panel" :class="{ active: activeTab === 'report' }" role="tabpanel" aria-labelledby="tab-btn-report" aria-label="招聘深度报表">
      <div data-slot="ai-workspace">
        <div data-slot="ai-conversation">
          <AiChatMessage role="ai" status="complete">
            <p>AI 分析招聘数据，标注异常趋势和关键洞察，并给出改进建议。选择报表类型开始分析。</p>
          </AiChatMessage>
          <AiChatMessage v-if="reportLoading" role="ai" status="loading" />
          <AiChatMessage v-if="reportError && !reportLoading" role="ai" status="error">
            <template #error>{{ reportError }}</template>
          </AiChatMessage>
          <template v-if="reportResult && !reportLoading">
            <AiChatMessage role="ai" status="complete">
              <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">综合分析</div><p data-slot="ai-report-summary">{{ reportResult.summary }}</p></div>
              <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">关键洞察</div><ul data-slot="ai-jd-list"><li v-for="(ins, i) in reportResult.insights" :key="'ins'+i">{{ ins }}</li></ul></div>
              <div v-if="reportResult.anomalies?.length" data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">异常标注</div><ul data-slot="ai-report-anomalies"><li v-for="(a, i) in reportResult.anomalies" :key="'an'+i">{{ a }}</li></ul></div>
              <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">改进建议</div><ul data-slot="ai-jd-list"><li v-for="(r, i) in reportResult.recommendations" :key="'rc'+i">{{ r }}</li></ul></div>
            </AiChatMessage>
            <AiDisclaimer />
          </template>
        </div>
        <div data-slot="ai-input-area">
          <div style="display:flex;gap:8px;margin-bottom:8px">
            <select v-model="reportForm.type" style="flex:1;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="报告类型">
              <option v-for="rt in reportTypes" :key="rt.value" :value="rt.value">{{ rt.label }}</option>
            </select>
          </div>
          <button class="btn btn-primary" style="width:100%" :disabled="reportLoading" @click="generateReport">
            <AiSkeleton v-if="reportLoading" variant="spinner" />
            {{ reportLoading ? '生成中...' : '生成分析报告' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ============ Tab 6: 候选人沟通助手 ============ -->
    <div class="tab-panel" :class="{ active: activeTab === 'chat' }" role="tabpanel" aria-labelledby="tab-btn-chat" aria-label="候选人沟通助手">
      <div data-slot="ai-workspace">
        <div data-slot="ai-conversation">
          <AiChatMessage role="ai" status="complete">
            <p>根据候选人信息和沟通场景，AI 生成沟通话术草稿和建议。所有联系动作必须由 HR 人工确认后执行。</p>
          </AiChatMessage>
          <AiChatMessage v-if="chatLoading" role="ai" status="loading" />
          <AiChatMessage v-if="chatError && !chatLoading" role="ai" status="error">
            <template #error>{{ chatError }}</template>
          </AiChatMessage>
          <template v-if="chatResult && !chatLoading">
            <AiChatMessage role="ai" status="complete">
              <div data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">话术草稿</div><div data-slot="ai-draft-text">{{ chatResult.draft }}</div></div>
              <div v-if="chatResult.suggestions?.length" data-slot="ai-jd-section"><div data-slot="ai-jd-section-title">沟通建议</div><ul data-slot="ai-jd-list"><li v-for="(s, si) in chatResult.suggestions" :key="'sg'+si">{{ s }}</li></ul></div>
            </AiChatMessage>
            <AiDisclaimer />
          </template>
        </div>
        <div data-slot="ai-input-area">
          <div style="display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap">
            <select v-model="chatForm.candidateId" style="flex:1;min-width:130px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="选择候选人">
              <option value="">选择候选人</option>
              <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <select v-model="chatForm.channel" style="flex:1;min-width:100px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="沟通渠道">
              <option value="">选择渠道</option>
              <option value="phone">电话</option><option value="email">邮件</option><option value="feishu">飞书</option>
            </select>
            <select v-model="chatForm.purpose" style="flex:1;min-width:120px;padding:7px 10px;border:1px solid var(--c-border);border-radius:var(--radius-sm);font-size:13px;font-family:inherit;background:var(--c-card);color:var(--c-body)" aria-label="沟通目的">
              <option value="">选择目的</option>
              <option v-for="p in purposes" :key="p.value" :value="p.value">{{ p.label }}</option>
            </select>
          </div>
          <button class="btn btn-primary" style="width:100%" :disabled="!chatForm.candidateId || !chatForm.channel || !chatForm.purpose || chatLoading" @click="generateDraft">
            <AiSkeleton v-if="chatLoading" variant="spinner" />
            {{ chatLoading ? '生成中...' : '生成沟通话术' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Toast notification (replaces alert) -->
    <Teleport to="body">
      <div v-if="toast.show" data-slot="ai-toast" :class="{ visible: toast.show }">{{ toast.text }}</div>
    </Teleport>

    <!-- BOSS 直聘集成 -->
    <div class="card" style="margin-top:24px">
      <div class="card-title">
        <svg viewBox="0 0 24 24" style="width:18px;height:18px;stroke:currentColor;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
        BOSS 直聘集成
        <span class="card-subtitle">基于 boss-cli 浏览器自动化</span>
      </div>
      <BossIntegration :compact="true" />
    </div>

    <!-- Embedded AI capabilities table -->
    <div style="margin-top:32px">
      <div class="section-label" style="font-size:14px;margin-bottom:12px">已嵌入各业务页面的辅助能力（流程内触发，不在此页面操作）</div>
      <div class="table-wrap">
        <table><thead><tr><th>辅助能力</th><th>所属页面</th><th>触发方式</th><th>Dify 工作流</th><th>状态</th></tr></thead>
          <tbody><tr v-for="(item, i) in embeddedAI" :key="i">
            <td v-html="item.ability"></td><td>{{ item.page }}</td><td>{{ item.trigger }}</td><td>{{ item.workflow }}</td>
            <td><StatusBadge :type="item.status">{{ statusLabel(item.status) }}</StatusBadge></td>
          </tr></tbody>
        </table>
      </div>
    </div>

    <!-- API architecture info -->
    <div class="permission-bar" style="margin-top:20px">
      <b>API 调用规则</b>：所有 AI 能力统一走 <b>后端代理转发</b>（前端 → Flask 后端 → DeepSeek），前端不直连 LLM。<br>
      <b>AI 工作流</b>：JD 草稿生成 · 语义简历搜索 · 人岗匹配 · 面试问题生成 · 沟通话术 · 招聘报表分析<br>
      <b>数据存储</b>：解析结果 → <code>t_hr_resume</code> · 匹配分 → <code>t_hr_resume_match</code> · 画像标签 → <code>t_hr_candidate</code><br>
      <b>免责声明</b>：所有 AI 生成内容仅供辅助参考，关键动作必须经过人工确认
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import StatusBadge from '../components/StatusBadge.vue';
import BossIntegration from '../components/BossIntegration.vue';
import AiPromptInput from '../components/ai/AiPromptInput.vue';
import AiChatMessage from '../components/ai/AiChatMessage.vue';
import AiSkeleton from '../components/ai/AiSkeleton.vue';
import AiDisclaimer from '../components/ai/AiDisclaimer.vue';
import { AI_TABS, EMBEDDED_AI, MOCK_CANDIDATES, MOCK_DEMANDS, MOCK_DEPARTMENTS } from '../data/ai.js';
import {
  runJdGenerate, runResumeSearch, runMatch as apiRunMatch,
  runInterviewQuestions, runCommunicationDraft, runReportAnalysis,
} from '../api/ai.js';
import { useStreaming } from '../composables/useStreaming.js';

// --- Shared ---
const tabs = AI_TABS;
const activeTab = ref('jd');
const embeddedAI = ref(EMBEDDED_AI);
const candidates = MOCK_CANDIDATES;
const demands = MOCK_DEMANDS;
const departments = MOCK_DEPARTMENTS;

// Toast (replaces alert)
const toast = reactive({ show: false, text: '', timer: null });
function showToast(text) {
  toast.text = text; toast.show = true;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { toast.show = false; }, 2000);
}
onBeforeUnmount(() => { clearTimeout(toast.timer); });

// --- Tab 1: JD ---
const levels = ['初级', '中级', '高级', '资深', '专家'];
const qualLabels = { education: '学历', experience: '经验', industry: '行业', soft: '软技能' };
const jdForm = reactive({ position: '', department: '', level: '高级', requirements: '' });
const jdResult = ref(null);
const jdLoading = ref(false);
const jdError = ref('');
const jdStatus = computed(() => jdLoading.value ? 'submitted' : (jdError.value ? 'error' : 'ready'));

// Streaming (SSE) — tries streaming first, falls back to blocking
const {
  content: jdStreamContent,
  thinking: jdThinking,
  isStreaming: jdStreaming,
  error: jdStreamError,
  result: jdStreamResult,
  start: startJdStream,
  stop: stopJdStream,
} = useStreaming();

// Watch for streaming completion and set jdResult
watch([jdStreamContent, jdStreamResult, jdStreamError], () => {
  if (jdStreamError.value) {
    jdError.value = jdStreamError.value;
    jdLoading.value = false;
  }
  if (!jdStreaming.value && jdStreamContent.value) {
    // Parse the markdown stream into structured JD
    jdResult.value = {
      jd_text: jdStreamContent.value,
      position: jdForm.position,
      department: jdForm.department,
      // Fallback structure if streaming didn't provide result keys
      responsibilities: jdStreamResult.value?.responsibilities || ['（AI 流式生成的内容，请查看上方 Markdown）'],
      required_skills: jdStreamResult.value?.required_skills || [],
      plus_skills: jdStreamResult.value?.plus_skills || [],
      qualifications: jdStreamResult.value?.qualifications || {},
      disclaimer: jdStreamResult.value?.disclaimer || '此内容由AI生成，请人工审核确认后使用',
    };
    jdLoading.value = false;
  }
});

// Watch: clear error on input change (must be after declarations)
watch(() => jdForm.requirements, () => { if (jdError.value) jdError.value = ''; });

// Override generateJd to use streaming
async function generateJd() {
  if (!jdForm.position || !jdForm.department) return;
  jdError.value = ''; jdLoading.value = true;
  try {
    await startJdStream('jd-generate', { ...jdForm });
    showToast('JD 草稿生成完成');
  } catch (e) {
    // Fallback to blocking API
    jdLoading.value = true;
    try {
      jdResult.value = await runJdGenerate({ ...jdForm });
      showToast('JD 草稿生成完成');
    } catch (e2) {
      jdError.value = e2.message || '生成失败，请重试';
      showToast(jdError.value);
    }
    jdLoading.value = false;
  }
}

// --- Tab 2: Search ---
const searchQuery = ref('');
const searchResults = ref([]);
const searchLoading = ref(false);
const searchError = ref('');
const searchAttempted = ref(false);
const searchStatus = computed(() => searchLoading.value ? 'submitted' : (searchError.value ? 'error' : 'ready'));

watch(searchQuery, () => { if (searchError.value) { searchError.value = ''; searchAttempted.value = false; } });

async function searchResume() {
  if (!searchQuery.value.trim()) return;
  searchError.value = ''; searchLoading.value = true; searchAttempted.value = true;
  try { const r = await runResumeSearch({ query: searchQuery.value, limit: 10 }); searchResults.value = r.results || []; showToast('找到 ' + searchResults.value.length + ' 位候选人'); }
  catch (e) { searchError.value = e.message || '搜索失败，请重试'; showToast(searchError.value); }
  finally { searchLoading.value = false; }
}
function viewResume(id) { showToast('查看简历: ' + id); }

// Streaming (SSE) for match
const {
  content: matchStreamContent,
  thinking: matchThinking,
  isStreaming: matchStreaming,
  error: matchStreamError,
  result: matchStreamResult,
  start: startMatchStream,
  stop: stopMatchStream,
} = useStreaming();

// Watch for match streaming completion
watch([matchStreamContent, matchStreamResult, matchStreamError], () => {
  if (matchStreamError.value) {
    matchError.value = matchStreamError.value;
    matchLoading.value = false;
  }
  if (!matchStreaming.value && matchStreamContent.value) {
    matchResult.value = {
      overall_score: matchStreamResult.value?.overall_score ?? 0,
      profile_score: matchStreamResult.value?.profile_score ?? 0,
      match_score: matchStreamResult.value?.match_score ?? 0,
      grade: matchStreamResult.value?.grade ?? 'B',
      reasons: matchStreamResult.value?.reasons || ['流式生成内容，请查看上方信息'],
      missing_skills: matchStreamResult.value?.missing_skills || [],
      strengths: matchStreamResult.value?.strengths || [],
      disclaimer: matchStreamResult.value?.disclaimer || '此内容由AI生成，请人工审核确认后使用',
    };
    matchLoading.value = false;
  }
});

// --- Tab 3: Match ---
const matchForm = reactive({ candidateId: '', demandId: '' });
const matchResult = ref(null);
const matchLoading = ref(false);
const matchError = ref('');

async function runMatch() {
  if (!matchForm.candidateId || !matchForm.demandId) return;
  matchError.value = ''; matchLoading.value = true;
  try {
    await startMatchStream('match', { candidate_id: matchForm.candidateId, demand_id: matchForm.demandId });
    showToast('匹配完成');
  } catch (e) {
    // Fallback to blocking API
    matchLoading.value = true;
    try {
      matchResult.value = await apiRunMatch({ candidate_id: matchForm.candidateId, demand_id: matchForm.demandId });
      showToast('匹配完成');
    } catch (e2) {
      matchError.value = e2.message || '匹配失败，请重试';
      showToast(matchError.value);
    }
    matchLoading.value = false;
  }
}

// --- Tab 4: Interview ---
const interviewRounds = [{ value: 'first', label: '初试' }, { value: 'second', label: '复试' }, { value: 'final', label: '终面' }];
const interviewForm = reactive({ candidateId: '', demandId: '', round: 'first' });
const interviewQuestions = ref([]);
const interviewLoading = ref(false);
const interviewError = ref('');
const expandedHints = ref({});

async function generateQuestions() {
  if (!interviewForm.candidateId || !interviewForm.demandId) return;
  interviewError.value = ''; interviewLoading.value = true;
  try { const r = await runInterviewQuestions({ candidate_id: interviewForm.candidateId, demand_id: interviewForm.demandId, round: interviewForm.round }); interviewQuestions.value = r.questions || []; expandedHints.value = {}; showToast('生成 ' + interviewQuestions.value.length + ' 个问题'); }
  catch (e) { interviewError.value = e.message || '生成失败，请重试'; showToast(interviewError.value); }
  finally { interviewLoading.value = false; }
}
function toggleHint(i) { expandedHints.value = { ...expandedHints.value, [i]: !expandedHints.value[i] }; }

// --- Tab 5: Report ---
const reportTypes = [
  { value: 'funnel', label: '招聘漏斗' }, { value: 'channel', label: '渠道效果' },
  { value: 'cycle', label: '招聘周期' }, { value: 'offer', label: 'Offer分析' }, { value: 'interviewer', label: '面试官统计' },
];
const reportForm = reactive({ type: 'funnel' });
const reportResult = ref(null);
const reportLoading = ref(false);
const reportError = ref('');

async function generateReport() {
  reportError.value = ''; reportLoading.value = true;
  try { reportResult.value = await runReportAnalysis({ type: reportForm.type, params: {} }); showToast('分析报告生成完成'); }
  catch (e) { reportError.value = e.message || '报告生成失败，请重试'; showToast(reportError.value); }
  finally { reportLoading.value = false; }
}

// --- Tab 6: Communication ---
const purposes = [
  { value: 'first_contact', label: '初次联系' }, { value: 'interview_invite', label: '面试邀请' },
  { value: 'offer_notice', label: 'Offer通知' }, { value: 'follow_up', label: '跟进' },
];
const chatForm = reactive({ candidateId: '', channel: '', purpose: '', context: '' });
const chatResult = ref(null);
const chatLoading = ref(false);
const chatError = ref('');

async function generateDraft() {
  if (!chatForm.candidateId || !chatForm.channel || !chatForm.purpose) return;
  chatError.value = ''; chatLoading.value = true;
  try { const c = candidates.find(x => x.id === chatForm.candidateId); chatResult.value = await runCommunicationDraft({ candidate_name: c?.name || '', channel: chatForm.channel, purpose: chatForm.purpose, context: chatForm.context }); showToast('话术生成完成'); }
  catch (e) { chatError.value = e.message || '话术生成失败，请重试'; showToast(chatError.value); }
  finally { chatLoading.value = false; }
}

// --- Shared ---
function statusLabel(s) { return { done: '一期开发', warn: '二期', draft: '远期' }[s] || s; }

// --- Keyboard navigation for tabs ---
function focusPrevTab() {
  const idx = tabs.findIndex(t => t.id === activeTab.value);
  const prev = tabs[idx > 0 ? idx - 1 : tabs.length - 1];
  activeTab.value = prev.id;
  document.getElementById('tab-btn-' + prev.id)?.focus();
}
function focusNextTab() {
  const idx = tabs.findIndex(t => t.id === activeTab.value);
  const next = tabs[idx < tabs.length - 1 ? idx + 1 : 0];
  activeTab.value = next.id;
  document.getElementById('tab-btn-' + next.id)?.focus();
}
</script>

<style scoped>
/* ===== AI Workspace layout (conversation + fixed input) ===== */
[data-slot="ai-workspace"] {
  display: flex;
  flex-direction: column;
  min-height: 420px;
  max-height: calc(100vh - 280px);
}
[data-slot="ai-conversation"] {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px;
}
[data-slot="ai-input-area"] {
  padding-top: 12px;
  border-top: 1px solid var(--c-border-light);
  flex-shrink: 0;
}

/* ===== JD Result sections ===== */
[data-slot="ai-jd-result"] { font-size: 13px; }
[data-slot="ai-jd-header"] { display:flex;align-items:center;gap:10px;margin-bottom:16px;padding-bottom:12px;border-bottom:1px solid var(--c-border) }
[data-slot="ai-jd-header"] h4 { font-size:16px;font-weight:700;color:var(--c-text);margin:0 }
[data-slot="ai-jd-dept"] { font-size:11px;color:var(--c-sub);background:var(--c-surface-elevated);padding:2px 10px;border-radius:10px;border:1px solid var(--c-border) }
[data-slot="ai-jd-section"] { margin-bottom:16px }
[data-slot="ai-jd-section-title"] { font-size:12px;font-weight:700;color:var(--c-text);margin-bottom:8px;display:flex;align-items:center;gap:6px }
[data-slot="ai-jd-section-title"]::before { content:'';width:3px;height:12px;background:var(--c-primary);border-radius:2px }
[data-slot="ai-jd-list"] { padding-left:16px;font-size:13px;color:var(--c-body);line-height:2 }
[data-slot="ai-jd-skill-table"] { border:1px solid var(--c-border);border-radius:var(--radius-sm);overflow:hidden }
[data-slot="ai-jd-skill-row"] { display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid var(--c-border-light);font-size:12px }
[data-slot="ai-jd-skill-row"]:last-child { border-bottom:none }
[data-slot="ai-jd-skill-name"] { font-weight:600;color:var(--c-text);min-width:70px }
[data-slot="ai-jd-skill-desc"] { color:var(--c-sub);flex:1 }
[data-slot="ai-jd-info-grid"] { display:grid;grid-template-columns:1fr 1fr;gap:6px }
[data-slot="ai-jd-info-item"] { display:flex;gap:6px;padding:6px 0;font-size:12px }
[data-slot="ai-jd-info-label"] { color:var(--c-sub);min-width:40px;flex-shrink:0 }
[data-slot="ai-jd-info-value"] { color:var(--c-text);font-weight:500 }

/* ===== Match result ===== */
[data-slot="ai-match-result"] { font-size:13px }
[data-slot="ai-match-score-row"] { display:flex;gap:24px;align-items:flex-start;margin-bottom:20px;padding-bottom:16px;border-bottom:1px solid var(--c-border) }
[data-slot="ai-match-big-score"] { text-align:center;min-width:100px }
[data-slot="ai-match-score-num"] { display:block;font-size:42px;font-weight:900;color:var(--c-primary);font-variant-numeric:tabular-nums;line-height:1 }
[data-slot="ai-match-score-label"] { font-size:11px;color:var(--c-sub);margin-top:2px;display:block }
[data-slot="ai-match-breakdown"] { flex:1 }
[data-slot="ai-match-item"] { display:flex;align-items:center;gap:10px;margin-bottom:8px;font-size:12px }
[data-slot="ai-match-item-label"] { color:var(--c-sub);min-width:48px }
[data-slot="ai-match-item-val"] { font-weight:700;color:var(--c-text);font-variant-numeric:tabular-nums;min-width:24px }

/* ===== Interview ===== */
[data-slot="ai-question-list"] { padding-left:18px }
[data-slot="ai-question-item"] { padding:12px 0;border-bottom:1px solid var(--c-border-light) }
[data-slot="ai-question-item"]:last-child { border-bottom:none }
[data-slot="ai-question-text"] { font-size:14px;color:var(--c-text);font-weight:600;line-height:1.6;margin-bottom:6px }
[data-slot="ai-question-meta"] { display:flex;align-items:center;gap:8px }
[data-slot="ai-question-hint"] { margin-top:8px;padding:10px 12px;background:var(--c-surface-elevated);border-radius:var(--radius-sm);border:1px solid var(--c-border-light);font-size:12px;color:var(--c-body);line-height:1.7 }

/* ===== Report ===== */
[data-slot="ai-report-summary"] { font-size:13px;line-height:1.9;color:var(--c-body) }
[data-slot="ai-report-anomalies"] { padding-left:16px;font-size:13px;line-height:2 }
[data-slot="ai-report-anomalies"] li { color:var(--c-warn) }
[data-slot="ai-report-anomalies"] li::marker { color:var(--c-warn) }

/* ===== Communication ===== */
[data-slot="ai-draft-text"] { font-size:13px;color:var(--c-body);background:var(--c-surface-elevated);padding:14px;border-radius:var(--radius-sm);border:1px solid var(--c-border-light);white-space:pre-wrap;line-height:1.8 }

/* ===== Toast ===== */
[data-slot="ai-toast"] {
  position: fixed;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%) translateY(20px);
  background: var(--c-sidebar,#1E293B);
  color: #fff;
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 9999;
  opacity: 0;
  transition: opacity .2s, transform .2s;
  pointer-events: none;
}
[data-slot="ai-toast"].visible {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

/* ===== Shared ===== */
.permission-bar { font-size:var(--fs-caption);color:var(--c-sub);background:var(--c-surface-elevated);padding:10px 16px;border-radius:var(--radius);border:1px solid var(--c-border);line-height:1.8;margin-bottom:var(--gap) }
.card { background:var(--c-card);border-radius:var(--radius);padding:20px;border:1px solid var(--c-border) }
.card-title { font-size:15px;font-weight:700;margin-bottom:16px;color:var(--c-text);display:flex;align-items:center;gap:8px }
.card-subtitle { font-size:11px;color:var(--c-sub);font-weight:400;margin-left:8px }
.section-label { font-size:14px;font-weight:600;color:var(--c-text) }
.reason-tags { display:flex;flex-wrap:wrap;gap:4px }
.reason-tag { display:inline-block;font-size:11px;color:var(--c-progress);background:var(--c-primary-subtle);padding:2px 8px;border-radius:4px }
.num-cell { text-align:right;font-variant-numeric:tabular-nums;font-feature-settings:"tnum";font-weight:600 }
.progress-bar { flex:1;height:8px;background:var(--c-border-light);border-radius:4px;overflow:hidden }
.progress-fill { height:100%;background:var(--c-primary);border-radius:4px;transition:width .6s ease }

@media (prefers-reduced-motion: reduce) {
  .progress-fill { transition: none; }
  [data-slot="ai-toast"] { transition: none; }
}

/* Focus visible */
input:focus-visible, select:focus-visible { outline:2px solid var(--c-primary);outline-offset:1px }
</style>
