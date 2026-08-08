<template>
  <div data-slot="ai-workspace">
    <AiConversation>
      <AiChatMessage role="ai" status="complete">
        <p>用自然语言描述你需要的候选人，系统解析语义后在人才库中搜索匹配简历。不用拼关键词，直接描述即可。</p>
      </AiChatMessage>
      <AiThinking
        v-if="searchProcHasRun"
        :steps="searchProcSteps"
        :active="searchProcActive"
        title="处理过程"
        done-text="处理完成 · 点击展开"
      />
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
    </AiConversation>
    <div data-slot="ai-input-area">
      <AiPromptInput
        v-model="searchQuery"
        :status="searchStatus"
        placeholder="描述你想要的候选人，例如：5年Java 大厂背景 做过微服务架构 熟悉K8s..."
        hint="Enter 快速搜索"
        layout="compact"
        aria-label="搜索描述"
        @submit="searchResume"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, inject } from 'vue';
import AiConversation from '../../components/ai/AiConversation.vue';
import AiThinking from '../../components/ai/AiThinking.vue';
import AiChatMessage from '../../components/ai/AiChatMessage.vue';
import AiPromptInput from '../../components/ai/AiPromptInput.vue';
import AiSkeleton from '../../components/ai/AiSkeleton.vue';
import { runResumeSearch } from '../../api/ai.js';
import { useProcessingSteps } from '../../composables/useProcessingSteps.js';

const showToast = inject('showToast');

const searchQuery = ref('');
const searchResults = ref([]);
const searchLoading = ref(false);
const searchError = ref('');
const searchAttempted = ref(false);
const searchStatus = computed(() => searchLoading.value ? 'submitted' : (searchError.value ? 'error' : 'ready'));

// 分步处理过程（诚实标注为「处理过程」，非真实 AI 思考链）
const {
  steps: searchProcSteps,
  active: searchProcActive,
  hasRun: searchProcHasRun,
  start: searchProcStart,
  finish: searchProcFinish,
} = useProcessingSteps(['理解搜索需求', '解析语义条件', '检索人才库', '排序匹配结果']);

watch(searchQuery, () => { if (searchError.value) { searchError.value = ''; searchAttempted.value = false; } });

async function searchResume() {
  if (!searchQuery.value.trim()) return;
  searchError.value = ''; searchLoading.value = true; searchAttempted.value = true;
  searchProcStart();
  try { const r = await runResumeSearch({ query: searchQuery.value, limit: 10 }); searchResults.value = r.results || []; showToast('找到 ' + searchResults.value.length + ' 位候选人'); }
  catch (e) { searchError.value = e.message || '搜索失败，请重试'; showToast(searchError.value); }
  finally { searchProcFinish(); searchLoading.value = false; }
}
function viewResume(id) { showToast('查看简历: ' + id + ' — 请前往人才库页面搜索查看'); }
</script>

<style scoped>
/* ===== AI Workspace layout ===== */
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

.reason-tags { display:flex;flex-wrap:wrap;gap:4px }
.reason-tag { display:inline-block;font-size:11px;color:var(--c-progress);background:var(--c-primary-subtle);padding:2px 8px;border-radius:4px }
.num-cell { text-align:right;font-variant-numeric:tabular-nums;font-feature-settings:"tnum";font-weight:600 }

/* ===== Mobile (≤768px) ===== */
@media (max-width: 768px) {
  .table-wrap {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .table-wrap table {
    min-width: 600px;
  }
  .reason-tag {
    font-size: 10px;
    padding: 2px 6px;
  }
}

/* Focus visible */
input:focus-visible, select:focus-visible { outline:2px solid var(--c-primary);outline-offset:1px }
</style>
