<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { getKnowledgeBase, type KnowledgeBase } from '@/api/knowledgeBases'
import { errorMessage } from '@/api/http'
import { getIngestionJob, listDocuments, uploadMarkdown, type IngestionJob, type IngestionStage, type KnowledgeDocument } from '@/api/ingestion'

const route = useRoute()
const workspaceId = computed(() => String(route.params.workspaceId ?? ''))
const kbId = computed(() => String(route.params.kbId ?? ''))
const knowledgeBase = ref<KnowledgeBase | null>(null)
const documents = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const search = ref('')
const filteredDocuments = computed(() => documents.value.filter((item) => item.displayName.toLowerCase().includes(search.value.trim().toLowerCase())))
const activeCount = computed(() => documents.value.filter((item) => item.ingestionJob && !isTerminal(item.ingestionJob)).length)
const failedCount = computed(() => documents.value.filter((item) => item.ingestionJob?.status === 'FAILED').length)
let pollingTimer: ReturnType<typeof setInterval> | undefined

const stageOrder: IngestionStage[] = ['PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING', 'COMPLETED']
const stageLabels: Record<IngestionStage, string> = {
  PENDING: '等待处理', PARSING: '解析', CHUNKING: '分块',
  EMBEDDING: '向量化', INDEXING: '建索引', COMPLETED: '完成',
}

async function load() {
  if (!workspaceId.value || !kbId.value) return
  stopPolling()
  loading.value = true
  try {
    const [loadedKnowledgeBase, loadedDocuments] = await Promise.all([
      getKnowledgeBase(workspaceId.value, kbId.value),
      listDocuments(workspaceId.value, kbId.value),
    ])
    knowledgeBase.value = loadedKnowledgeBase
    documents.value = loadedDocuments
    ensurePolling()
  } catch (error) {
    knowledgeBase.value = null
    documents.value = []
    void MessagePlugin.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function selectFile() { fileInput.value?.click() }

async function handleFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    const result = await uploadMarkdown(workspaceId.value, kbId.value, file)
    documents.value = await listDocuments(workspaceId.value, kbId.value)
    void MessagePlugin.success(result.duplicate ? '文件内容已存在，已返回原任务' : '上传成功，已创建入库任务')
    ensurePolling()
  } catch (error) {
    void MessagePlugin.error(errorMessage(error))
  } finally {
    uploading.value = false
  }
}

function isTerminal(job: IngestionJob) {
  return ['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status)
}

function stageProgress(job: IngestionJob) {
  const index = stageOrder.indexOf(job.stage)
  return Math.max(0, Math.round((index / (stageOrder.length - 1)) * 100))
}

function displayTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function pollJobs() {
  const active = documents.value.filter((document) => document.ingestionJob && !isTerminal(document.ingestionJob))
  if (!active.length) {
    stopPolling()
    return
  }
  await Promise.all(active.map(async (document) => {
    try {
      if (document.ingestionJob) {
        document.ingestionJob = await getIngestionJob(workspaceId.value, document.ingestionJob.id)
      }
    } catch {
      // 临时网络错误留到下一轮重试，不把任务错误误判为失败。
    }
  }))
}

function ensurePolling() {
  if (!pollingTimer && documents.value.some((document) => document.ingestionJob && !isTerminal(document.ingestionJob))) {
    pollingTimer = setInterval(() => void pollJobs(), 2000)
  }
}

function stopPolling() {
  if (pollingTimer) clearInterval(pollingTimer)
  pollingTimer = undefined
}

onMounted(load)
watch([workspaceId, kbId], load)
onBeforeUnmount(stopPolling)
</script>

<template>
  <section class="page">
    <div class="breadcrumb"><router-link :to="`/workspaces/${workspaceId}/knowledge-bases`">知识库</router-link><span>/</span><span>{{ knowledgeBase?.name || '文档' }}</span></div>
    <t-loading :loading="loading">
      <template v-if="knowledgeBase">
        <header>
          <div><span class="eyebrow">KNOWLEDGE BASE</span><h1>{{ knowledgeBase.name }}</h1><p>{{ knowledgeBase.description || '管理文档与入库状态' }}</p></div>
          <t-button theme="primary" :loading="uploading" @click="selectFile">上传 Markdown</t-button>
        </header>

        <div class="summary">
          <div><span>文档总数</span><strong>{{ documents.length }}</strong></div>
          <div><span>处理中</span><strong>{{ activeCount }}</strong></div>
          <div><span>失败任务</span><strong>{{ failedCount }}</strong></div>
        </div>

        <t-card class="document-card">
          <div class="section-heading"><div><h2>文档</h2><p>查看当前版本与入库进度</p></div><t-input v-model="search" class="search" placeholder="搜索文档名称" clearable /></div>
            <input ref="fileInput" class="file-input" type="file" accept=".md,text/markdown,text/plain" @change="handleFile" />

          <div v-if="filteredDocuments.length" class="documents">
            <article v-for="document in filteredDocuments" :key="document.id" class="document">
              <div class="document-heading">
                <div>
                  <strong>{{ document.displayName }}</strong>
                  <p>版本 v{{ document.currentVersion.versionNumber }} · 上传于 {{ displayTime(document.createdAt) }}</p>
                </div>
                <t-tag v-if="document.ingestionJob" :theme="document.ingestionJob.status === 'FAILED' ? 'danger' : document.ingestionJob.status === 'COMPLETED' ? 'success' : 'primary'" variant="light">
                  {{ document.ingestionJob.status }}
                </t-tag>
                <t-tag v-else theme="warning" variant="light">任务缺失</t-tag>
              </div>

              <template v-if="document.ingestionJob">
                <p class="stage">{{ stageLabels[document.ingestionJob.stage] }} · 尝试 {{ document.ingestionJob.attemptCount }}/{{ document.ingestionJob.maxAttempts }}</p>
                <t-progress :percentage="stageProgress(document.ingestionJob)" />
                <p v-if="document.ingestionJob.errorMessage" class="error">
                  {{ document.ingestionJob.errorCode || 'INGESTION_FAILED' }}：{{ document.ingestionJob.errorMessage }}
                </p>
                <code>Job {{ document.ingestionJob.id }}</code>
              </template>
              <details class="metadata"><summary>查看文档标识</summary><code>SHA-256 {{ document.currentVersion.contentSha256 }}</code><code>Document {{ document.id }}</code></details>
            </article>
          </div>
          <t-empty v-else :description="search ? '没有匹配的文档' : '尚未上传 Markdown 文档'" />
        </t-card>
      </template>
      <t-empty v-else-if="!loading" description="知识库不存在或暂时无法加载" />
    </t-loading>
  </section>
</template>

<style scoped>
.page { max-width: 1280px; margin: 0 auto; }
.breadcrumb { display: flex; gap: 10px; margin-bottom: 24px; color: var(--td-text-color-placeholder); font-size: 13px; }
.breadcrumb a { color: var(--td-brand-color); text-decoration: none; }
header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 28px; }
.eyebrow { color: var(--td-brand-color); font-size: 11px; font-weight: 700; letter-spacing: .12em; }
header h1 { margin: 8px 0; font-size: 30px; }
header p { margin: 0; color: var(--td-text-color-secondary); }
.summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 24px; }
.summary div { display: grid; gap: 8px; padding: 20px 24px; background: #fff; border: 1px solid var(--td-component-border); border-radius: 12px; }
.summary span { color: var(--td-text-color-secondary); font-size: 13px; }
.summary strong { font-size: 28px; line-height: 1; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.section-heading h2 { margin: 0 0 4px; font-size: 18px; }
.section-heading p { margin: 0; color: var(--td-text-color-secondary); font-size: 13px; }
.search { width: min(260px, 100%); }
.file-input { display: none; }
.documents { display: grid; }
.document { padding: 18px 2px; border-top: 1px solid var(--td-component-border); }
.document-heading { display: flex; align-items: start; justify-content: space-between; gap: 16px; }
.document-heading p, .stage { margin: 6px 0 12px; color: var(--td-text-color-secondary); }
.document code { display: block; margin-top: 10px; color: var(--td-text-color-placeholder); overflow-wrap: anywhere; }
.metadata { margin-top: 10px; font-size: 12px; color: var(--td-text-color-placeholder); }
.metadata summary { cursor: pointer; }
.error { color: var(--td-error-color); }
@media (max-width: 680px) { header, .section-heading { align-items: stretch; flex-direction: column; } .summary { gap: 8px; } .summary div { padding: 14px; } .summary strong { font-size: 22px; } .search { width: 100%; } }
</style>
