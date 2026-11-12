<script setup lang="ts">
// 检索调试页：执行 Production RAG 并展示各阶段状态、候选顺序、分数和降级原因。
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { errorMessage } from '@/api/http'
import { debugRetrieval, type RetrievalPipelineResult, type RetrievalStage } from '@/api/retrieval'

const route = useRoute()
const workspaceId = computed(() => String(route.params.workspaceId ?? ''))
const kbId = computed(() => String(route.params.kbId ?? ''))
const question = ref('')
const loading = ref(false)
const result = ref<RetrievalPipelineResult | null>(null)
const selectedStage = ref<RetrievalStage>('FUSION')
const candidates = computed(() => result.value?.stages.filter((item) => item.stage === selectedStage.value) ?? [])

async function run() {
  const normalized = question.value.trim()
  if (!normalized) return
  loading.value = true
  try {
    result.value = await debugRetrieval(workspaceId.value, kbId.value, normalized)
    selectedStage.value = result.value.stageSummaries.find((item) => item.candidateCount > 0)?.stage ?? 'FUSION'
  } catch (error) {
    void MessagePlugin.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page">
    <div class="breadcrumb"><router-link :to="`/workspaces/${workspaceId}/knowledge-bases/${kbId}`">知识库</router-link><span>/</span><span>检索调试</span></div>
    <header><div><span class="eyebrow">RETRIEVAL DEBUG</span><h1>检索流水线</h1><p>观察 Vector、BM25、RRF、Rerank 与最终上下文。</p></div></header>
    <t-card>
      <div class="query"><t-textarea v-model="question" placeholder="输入用于检索的问题" :maxlength="2000" autosize /><t-button theme="primary" :loading="loading" @click="run">运行检索</t-button></div>
    </t-card>

    <template v-if="result">
      <div class="run-summary"><span>Run {{ result.runId }}</span><strong>最终阶段：{{ result.finalStage }}</strong><t-tag v-if="result.degradationCode" theme="warning">{{ result.degradationCode }}</t-tag></div>
      <div class="stages">
        <button v-for="summary in result.stageSummaries" :key="summary.stage" type="button" :class="{ selected: selectedStage === summary.stage }" @click="selectedStage = summary.stage">
          <span>{{ summary.stage }}</span><strong>{{ summary.candidateCount }}</strong><small>{{ summary.status }} · {{ summary.latencyMs }} ms</small><em v-if="summary.errorCode">{{ summary.errorCode }}</em>
        </button>
      </div>
      <t-card class="candidate-card">
        <div class="section-heading"><div><h2>{{ selectedStage }} 候选</h2><p>分数只在当前阶段和本次运行内解释。</p></div></div>
        <article v-for="item in candidates" :key="`${item.stage}-${item.rank}-${item.hit.chunkId}`" class="candidate">
          <div class="candidate-head"><strong>#{{ item.rank }} · {{ item.hit.documentName }} · Chunk {{ item.hit.sequenceNumber }}</strong><code>{{ item.score.toFixed(6) }}</code></div>
          <p>{{ item.hit.content }}</p><small>Version {{ item.hit.documentVersionId }} · {{ item.hit.sourcePosition }}</small><pre>{{ JSON.stringify(item.metadata, null, 2) }}</pre>
        </article>
        <t-empty v-if="!candidates.length" description="该阶段没有候选；请查看阶段状态和错误码" />
      </t-card>
    </template>
  </section>
</template>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; }.breadcrumb { display: flex; gap: 10px; margin-bottom: 24px; color: var(--td-text-color-placeholder); font-size: 13px; }.breadcrumb a { color: var(--td-brand-color); text-decoration: none; }
header { margin-bottom: 24px; }.eyebrow { color: var(--td-brand-color); font-size: 11px; font-weight: 700; letter-spacing: .12em; }h1 { margin: 8px 0; }header p { margin: 0; color: var(--td-text-color-secondary); }.query { display: grid; grid-template-columns: 1fr auto; align-items: end; gap: 12px; }
.run-summary { display: flex; align-items: center; gap: 16px; margin: 22px 0 12px; color: var(--td-text-color-secondary); }.run-summary strong { color: var(--td-text-color-primary); }.stages { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-bottom: 16px; }.stages button { display: grid; gap: 5px; padding: 14px; text-align: left; border: 1px solid var(--td-component-border); border-radius: 10px; background: #fff; cursor: pointer; }.stages button.selected { border-color: var(--td-brand-color); box-shadow: 0 0 0 1px var(--td-brand-color); }.stages strong { font-size: 24px; }.stages small { color: var(--td-text-color-secondary); }.stages em { color: var(--td-warning-color); font-size: 11px; font-style: normal; }
.section-heading h2 { margin: 0 0 4px; }.section-heading p { margin: 0 0 14px; color: var(--td-text-color-secondary); }.candidate { padding: 16px 0; border-top: 1px solid var(--td-component-border); }.candidate-head { display: flex; justify-content: space-between; gap: 16px; }.candidate p { white-space: pre-wrap; color: var(--td-text-color-secondary); }.candidate small { color: var(--td-text-color-placeholder); }.candidate pre { overflow: auto; padding: 10px; border-radius: 8px; background: var(--td-bg-color-secondarycontainer); font-size: 11px; }
@media (max-width: 800px) { .query { grid-template-columns: 1fr; }.stages { grid-template-columns: repeat(2, 1fr); }.run-summary { align-items: flex-start; flex-direction: column; gap: 6px; } }
</style>
