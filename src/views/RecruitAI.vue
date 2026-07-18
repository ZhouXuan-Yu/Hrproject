<template>
  <WorkbenchLayout title="招聘辅助中心" :breadcrumb="{ text: '招聘管理', href: '/recruit-dashboard' }">
    <template #topbar-actions>
      <span class="dev-badge">二期开发中</span>
    </template>

    <div class="permission-bar">
      本页面仅<b>系统管理员</b>可见 · 集中所有<b>用户主动发起</b>的招聘辅助能力 · 流程内嵌的简历解析、匹配评分、联系话术辅助已嵌入各业务页面
    </div>

    <!-- 6 Tab navigation -->
    <div class="tabs" role="tablist">
      <button v-for="tab in tabs" :key="tab.id"
        class="tab"
        :class="{ active: activeTab === tab.id }"
        :aria-selected="activeTab === tab.id ? 'true' : 'false'"
        role="tab"
        @click="activeTab = tab.id"
      >{{ tab.number }} {{ tab.title }}</button>
    </div>

    <!-- Tab panels -->
    <div v-for="tab in tabs" :key="tab.id"
      class="tab-panel"
      :class="{ active: activeTab === tab.id }"
      role="tabpanel"
    >
      <div class="phase-card" style="margin-bottom:20px">
        <div style="position:relative">
          <span class="phase-badge" style="position:absolute;top:-8px;right:-8px">{{ currentPanel.badge }}</span>
          <h3>{{ currentPanel.title }}</h3>
        </div>
        <div class="panel-desc" v-html="currentPanel.desc"></div>
        <ul>
          <li v-for="(item, i) in currentPanel.items" :key="i" v-html="item"></li>
        </ul>
      </div>
    </div>

    <!-- Embedded AI capabilities table -->
    <div style="margin-top:32px">
      <div class="section-label" style="font-size:14px;margin-bottom:12px">已嵌入各业务页面的辅助能力（流程内触发，不在此页面操作）</div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>辅助能力</th><th>所属页面</th><th>触发方式</th><th>Dify 工作流</th><th>状态</th></tr>
          </thead>
          <tbody>
            <tr v-for="(item, i) in embeddedAI" :key="i">
              <td v-html="item.ability"></td>
              <td>{{ item.page }}</td>
              <td>{{ item.trigger }}</td>
              <td>{{ item.workflow }}</td>
              <td><StatusBadge :type="item.status">{{ statusLabel(item.status) }}</StatusBadge></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- API architecture info -->
    <div class="permission-bar" style="margin-top:20px">
      <b>API 调用规则</b>：所有 AI 能力统一走 <b>后端代理转发</b>（前端 → Flask 后端 → Dify），前端不直连 Dify。<br>
      <b>三个 Dify 工作流底座</b>：① 简历画像解析 &nbsp; ② 人岗匹配打分 &nbsp; ③ 面试问题生成 &nbsp; + 未来新增候选人沟通 / JD生成 / Offer / 入职<br>
      <b>数据存储</b>：解析结果 → <code>t_hr_resume</code> · 匹配分 → <code>t_hr_resume_match</code> · 画像标签 → <code>t_hr_candidate</code> + <code>t_hr_candidate_tag_rel</code><br>
      <b>远期辅助能力</b>：支持生成 Offer、入职包和会议安排草稿，关键动作必须经过人工确认
    </div>
  </WorkbenchLayout>
</template>

<script setup>
import { ref, computed } from 'vue';
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue';
import { AI_TABS, AI_PANELS, EMBEDDED_AI } from '../data/ai.js';

const tabs = AI_TABS;
const activeTab = ref('chat');
const embeddedAI = EMBEDDED_AI;

const currentPanel = computed(() => AI_PANELS[activeTab.value] || AI_PANELS['chat']);

function statusLabel(status){
  return { done: '一期开发', warn: '二期', draft: '远期' }[status] || status;
}
</script>

<style scoped>
.dev-badge {
  font-size: 11px;
  color: var(--c-warn);
  background: #FFF7ED;
  padding: 4px 10px;
  border-radius: 10px;
  font-weight: 600;
}
.tab {
  border: none;
  background: none;
  font-family: inherit;
  cursor: pointer;
}
.panel-desc {
  font-size: 13px;
  color: var(--c-sub);
  margin: 8px 0 14px;
  line-height: 1.8;
}
.panel-desc :deep(b) { color: var(--c-text); font-weight: 650; }
.phase-card ul {
  font-size: 13px;
  color: var(--c-text);
  padding-left: 18px;
  line-height: 2;
}
.phase-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
}
:deep(.phase-badge) {
  font-size: 10px;
  background: #F0F5FF;
  border: 1px solid var(--c-border);
  padding: 2px 8px;
  border-radius: 10px;
  white-space: nowrap;
}
</style>
