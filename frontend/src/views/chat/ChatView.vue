<script setup lang="ts">
// 知识问答工作台：按轮次展示问题、流式回答、状态和与当前回答绑定的检索引用。
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getChatSession, streamRagAnswer, type RagCitation } from '@/api/rag'
import { listKnowledgeBases, type KnowledgeBase } from '@/api/knowledgeBases'
import { errorMessage } from '@/api/http'
import { renderUntrustedMarkdown } from '@/utils/markdown'

type TurnStatus = 'streaming' | 'completed' | 'interrupted' | 'failed' | 'stopped'
interface ChatTurn { id: string; question: string; answer: string; citations: RagCitation[]; status: TurnStatus; progressText: string; error: string }

const route = useRoute()
const router = useRouter()
const workspaceId = computed(() => String(route.params.workspaceId ?? ''))
const routeKbId = computed(() => String(route.params.kbId ?? ''))
const selectedKbId = ref('')
const knowledgeBases = ref<KnowledgeBase[]>([])
const loadingKnowledgeBases = ref(false)
const question = ref('')
const turns = ref<ChatTurn[]>([])
const activeTurnId = ref<number | null>(null)
const conversation = ref<HTMLElement | null>(null)
const pageError = ref('')
const sessionId = ref<string | null>(null)
let controller: AbortController | null = null

const streaming = computed(() => turns.value.some((turn) => turn.status === 'streaming'))
const activeTurn = computed(() => turns.value.find((turn) => turn.id === activeTurnId.value) ?? turns.value.at(-1) ?? null)
const activeCitations = computed(() => activeTurn.value?.citations ?? [])

async function loadKnowledgeBases() {
  if (!workspaceId.value) return
  loadingKnowledgeBases.value = true
  pageError.value = ''
  try {
    knowledgeBases.value = await listKnowledgeBases(workspaceId.value)
    const requested = routeKbId.value
    selectedKbId.value = knowledgeBases.value.some((item) => item.id === requested) ? requested : (knowledgeBases.value[0]?.id ?? '')
    const requestedSession = typeof route.query.session === 'string' ? route.query.session : ''
    if (requestedSession && selectedKbId.value) await restoreSession(requestedSession)
  } catch (caught) {
    knowledgeBases.value = []
    selectedKbId.value = ''
    pageError.value = errorMessage(caught)
  } finally { loadingKnowledgeBases.value = false }
}

/** 从数据库恢复会话；刷新页面后仍可查看历史正文、终态和引用原文。 */
async function restoreSession(requestedSessionId: string) {
  try {
    const session = await getChatSession(workspaceId.value, selectedKbId.value, requestedSessionId)
    sessionId.value = session.id
    turns.value = session.turns.map((turn) => ({
      id: turn.assistantMessageId,
      question: turn.question,
      answer: turn.answer,
      citations: turn.citations,
      status: turn.status === 'COMPLETED' ? 'completed' : 'interrupted',
      progressText: '',
      error: turn.errorCode ? `上次生成未正常完成（${turn.errorCode}）` : '',
    }))
    activeTurnId.value = turns.value.at(-1)?.id ?? null
    void scrollToLatest()
  } catch (caught) {
    sessionId.value = null
    turns.value = []
    pageError.value = errorMessage(caught)
  }
}

async function scrollToLatest() {
  await nextTick()
  conversation.value?.scrollTo({ top: conversation.value.scrollHeight, behavior: 'smooth' })
}

/** 新建一轮对话；错误只标记本轮，保留已收到的部分回答和引用。 */
async function ask(text = question.value) {
  const value = text.trim()
  if (!value || !selectedKbId.value || streaming.value) return
  controller = new AbortController()
  const currentController = controller
  const turn = reactive<ChatTurn>({ id: crypto.randomUUID(), question: value, answer: '', citations: [], status: 'streaming', progressText: '正在连接知识服务…', error: '' })
  turns.value.push(turn)
  activeTurnId.value = turn.id
  question.value = ''
  void scrollToLatest()
  try {
    await streamRagAnswer(workspaceId.value, selectedKbId.value, sessionId.value, value, currentController.signal, (event) => {
      if (event.type === 'started' && event.sessionId) {
        sessionId.value = event.sessionId
        if (event.messageId) turn.id = event.messageId
        activeTurnId.value = turn.id
        void router.replace({ query: { ...route.query, session: event.sessionId } })
      }
      if (event.type === 'status' && event.text) turn.progressText = event.text
      if (event.type === 'citations') turn.citations = event.citations ?? []
      if (event.type === 'delta' && event.text) { turn.progressText = '正在生成回答…'; turn.answer += event.text; void scrollToLatest() }
      if (event.type === 'error') {
        turn.status = turn.answer ? 'interrupted' : 'failed'
        turn.error = event.text ?? (turn.answer ? '回答生成中断' : '问答失败')
      }
      if (event.type === 'completed') turn.status = 'completed'
    })
  } catch (caught) {
    if (!currentController.signal.aborted) {
      turn.status = turn.answer ? 'interrupted' : 'failed'
      turn.error = caught instanceof Error ? caught.message : '问答连接失败'
    }
  } finally {
    if (turn.status === 'streaming') turn.status = currentController.signal.aborted ? 'stopped' : 'completed'
    if (controller === currentController) controller = null
  }
}

function cancel() {
  const turn = turns.value.find((item) => item.status === 'streaming')
  controller?.abort()
  if (turn) turn.status = 'stopped'
  controller = null
}
function retry(turn: ChatTurn) { activeTurnId.value = turn.id; void ask(turn.question) }
function clearConversation() {
  cancel()
  turns.value = []
  activeTurnId.value = null
  sessionId.value = null
  const query = { ...route.query }
  delete query.session
  void router.replace({ query })
}
function selectTurn(turn: ChatTurn) { activeTurnId.value = turn.id }
function renderAnswer(value: string) { return renderUntrustedMarkdown(value) }
function similarity(value: number) { return `${Math.max(0, Math.min(100, value * 100)).toFixed(1)}%` }

onMounted(loadKnowledgeBases)
onBeforeUnmount(cancel)
watch(workspaceId, loadKnowledgeBases)
watch(selectedKbId, () => { if (turns.value.length) clearConversation() })
</script>

<template>
  <section class="chat-page">
    <div class="breadcrumb"><router-link :to="`/workspaces/${workspaceId}/knowledge-bases`">工作台</router-link><span>/</span><span>知识问答</span></div>
    <header class="page-header">
      <div><span class="eyebrow">RAG CHAT</span><h1>知识问答</h1><p>基于已入库文档回答，每轮回答都保留实际检索引用。</p></div>
      <div class="header-tools">
        <label class="knowledge-picker"><span>回答范围</span><t-select v-model="selectedKbId" :loading="loadingKnowledgeBases" :disabled="streaming || !knowledgeBases.length" placeholder="请选择知识库"><t-option v-for="item in knowledgeBases" :key="item.id" :value="item.id" :label="item.name" /></t-select></label>
        <t-button v-if="turns.length" variant="text" :disabled="streaming" @click="clearConversation">清空对话</t-button>
      </div>
    </header>

    <p v-if="pageError" class="page-error">{{ pageError }}</p>
    <div class="workspace">
      <main class="chat-panel">
        <div ref="conversation" class="conversation">
          <div v-if="!turns.length" class="welcome"><span class="welcome-icon">◇</span><h2>从知识库中寻找答案</h2><p>{{ knowledgeBases.length ? '输入一个具体问题，回答会在这里逐步生成。' : '请先创建知识库并完成文档入库。' }}</p></div>

          <article v-for="turn in turns" :key="turn.id" class="turn" :class="{ active: activeTurn?.id === turn.id }" @click="selectTurn(turn)">
            <div class="question-row"><span class="avatar user">你</span><div><span class="role">你的问题</span><p>{{ turn.question }}</p></div></div>
            <div class="answer-row"><span class="avatar assistant">P</span><div class="answer-body">
              <div class="answer-heading"><span class="role">PB-RAG</span><span v-if="turn.status === 'streaming'" class="generating"><i></i>{{ turn.progressText }}</span><span v-else-if="turn.status === 'completed'" class="done">回答完成</span></div>
              <div v-if="turn.answer" class="answer" v-html="renderAnswer(turn.answer)"></div>
              <div v-else-if="turn.status === 'streaming'" class="thinking"><i></i><i></i><i></i></div>
              <p v-if="turn.status === 'interrupted'" class="turn-warning">{{ turn.error || '回答生成中断，已保留收到的内容。' }}</p>
              <p v-else-if="turn.status === 'failed'" class="turn-error">{{ turn.error || '本次问答失败。' }}</p>
              <p v-else-if="turn.status === 'stopped'" class="turn-muted">已停止生成</p>
              <div class="turn-actions"><button type="button" @click.stop="selectTurn(turn)">{{ turn.citations.length }} 条引用</button><button v-if="['interrupted', 'failed', 'stopped'].includes(turn.status)" type="button" :disabled="streaming" @click.stop="retry(turn)">重新生成</button></div>
            </div></div>
          </article>
        </div>

        <form class="composer" @submit.prevent="ask()">
          <textarea v-model="question" maxlength="2000" rows="3" :disabled="!selectedKbId" placeholder="输入你想从知识库了解的问题…" @keydown.ctrl.enter.prevent="ask()"></textarea>
          <div><span>Ctrl + Enter 发送 · {{ question.length }}/2000</span><t-button v-if="streaming" theme="danger" variant="outline" type="button" @click="cancel">停止生成</t-button><t-button v-else theme="primary" type="submit" :disabled="!question.trim() || !selectedKbId">发送</t-button></div>
        </form>
      </main>

      <aside class="citations-panel">
        <div class="citations-heading"><div><h2>检索引用</h2><p>{{ activeTurn ? '当前选中回答的证据' : '选择回答后查看证据' }}</p></div><span>{{ activeCitations.length }} 条</span></div>
        <div class="citation-list"><article v-for="citation in activeCitations" :key="citation.chunkId" class="citation"><strong>[{{ citation.index }}] {{ citation.documentName }}</strong><p>Chunk {{ citation.sequenceNumber + 1 }} · 相似度 {{ similarity(citation.similarity) }}</p><blockquote>{{ citation.excerpt }}</blockquote><details><summary>查看来源位置</summary><code>{{ citation.sourcePosition }}</code><code>Version {{ citation.documentVersionId }}</code></details></article><t-empty v-if="!activeCitations.length" description="回答后显示实际引用" /></div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.chat-page{max-width:1380px;margin:0 auto}.breadcrumb{display:flex;gap:10px;margin-bottom:20px;color:var(--td-text-color-placeholder);font-size:13px}.breadcrumb a{color:var(--td-brand-color);text-decoration:none}.page-header{display:flex;align-items:end;justify-content:space-between;gap:28px}.eyebrow{color:var(--td-brand-color);font-size:11px;font-weight:800;letter-spacing:.12em}.page-header h1{margin:7px 0 5px;font-size:30px}.page-header p{margin:0;color:var(--td-text-color-secondary)}.header-tools{display:flex;align-items:end;gap:8px}.knowledge-picker{display:grid;width:300px;gap:6px}.knowledge-picker>span{color:var(--td-text-color-secondary);font-size:12px;font-weight:600}.page-error{padding:11px 14px;border-radius:9px;background:#fff1f0;color:#c53f3f}
.workspace{display:grid;grid-template-columns:minmax(0,1fr) 340px;gap:18px;margin-top:22px;height:calc(100vh - 245px);min-height:600px}.chat-panel,.citations-panel{min-height:0;background:#fff;border:1px solid var(--td-component-border);border-radius:14px}.chat-panel{display:flex;flex-direction:column;overflow:hidden}.conversation{flex:1;overflow-y:auto;padding:28px 34px}.welcome{display:grid;place-items:center;height:100%;align-content:center;text-align:center;color:var(--td-text-color-secondary)}.welcome-icon{display:grid;place-items:center;width:52px;height:52px;border-radius:16px;background:#edf2ff;color:#3159db;font-size:27px}.welcome h2{margin:16px 0 7px;color:var(--td-text-color-primary);font-size:19px}.welcome p{margin:0}
.turn{padding:6px 4px 28px;border-radius:10px}.turn+.turn{padding-top:26px;border-top:1px solid #edf0f5}.turn.active{background:linear-gradient(90deg,#f7f9ff 0,transparent 52%)}.question-row,.answer-row{display:grid;grid-template-columns:34px minmax(0,1fr);gap:12px}.question-row{margin-bottom:22px}.question-row p{margin:5px 0 0;font-size:16px;line-height:1.6}.avatar{display:grid;place-items:center;width:32px;height:32px;border-radius:10px;font-size:12px;font-weight:800}.avatar.user{background:#eef1f6;color:#596980}.avatar.assistant{background:#3159db;color:#fff}.role{color:#77849a;font-size:11px;font-weight:700;letter-spacing:.04em}.answer-body{min-width:0}.answer-heading{display:flex;align-items:center;gap:10px;margin-bottom:7px}.generating,.done{font-size:11px}.generating{color:#3159db}.generating i{display:inline-block;width:6px;height:6px;margin-right:5px;border-radius:50%;background:#3159db;animation:pulse 1s infinite}.done{color:#6d7b91}.answer{line-height:1.75}.thinking{display:flex;gap:5px;padding:12px 0}.thinking i{width:7px;height:7px;border-radius:50%;background:#8da5ef;animation:bounce 1.1s infinite}.thinking i:nth-child(2){animation-delay:.15s}.thinking i:nth-child(3){animation-delay:.3s}.turn-warning,.turn-error,.turn-muted{margin:12px 0 0;padding:9px 11px;border-radius:8px;font-size:13px}.turn-warning{background:#fff7e6;color:#a56400}.turn-error{background:#fff1f0;color:#c53f3f}.turn-muted{background:#f3f5f8;color:#77849a}.turn-actions{display:flex;gap:14px;margin-top:12px}.turn-actions button{padding:0;border:0;background:none;color:#3159db;cursor:pointer;font-size:12px}.turn-actions button:disabled{color:#aab3c1;cursor:not-allowed}
.composer{flex:none;margin:0 24px 22px;padding:13px 14px 10px;border:1px solid #d9e0eb;border-radius:12px;background:#fff;box-shadow:0 10px 28px #243c6a12}.composer:focus-within{border-color:#7e99eb;box-shadow:0 0 0 3px #3159db12}.composer textarea{box-sizing:border-box;width:100%;min-height:72px;padding:0;border:0;outline:none;resize:none;font:inherit;line-height:1.55}.composer>div{display:flex;align-items:center;justify-content:space-between;margin-top:7px}.composer span{color:var(--td-text-color-placeholder);font-size:12px}
.citations-panel{display:flex;flex-direction:column;padding:22px 16px 12px;overflow:hidden}.citations-heading{display:flex;align-items:start;justify-content:space-between;padding:0 10px 15px;border-bottom:1px solid var(--td-component-border)}.citations-heading h2{margin:0 0 4px;font-size:17px}.citations-heading p{margin:0;color:var(--td-text-color-placeholder);font-size:11px}.citations-heading>span{color:var(--td-text-color-placeholder);font-size:12px}.citation-list{overflow-y:auto;padding:0 10px}.citation{padding:17px 0;border-bottom:1px solid var(--td-component-border)}.citation strong{font-size:13px}.citation p{margin:7px 0;color:var(--td-text-color-secondary);font-size:12px}.citation blockquote{max-height:150px;margin:10px 0;padding:10px 11px;overflow:auto;border-left:3px solid #9bb0ee;border-radius:0 7px 7px 0;background:#f7f9fd;color:#4e5c72;font-size:12px;line-height:1.6;white-space:pre-wrap}.citation details{font-size:11px;color:var(--td-text-color-placeholder)}.citation summary{cursor:pointer}.citation code{display:block;margin-top:6px;overflow-wrap:anywhere;white-space:pre-wrap}
@keyframes pulse{50%{opacity:.35}}@keyframes bounce{0%,60%,100%{transform:translateY(0)}30%{transform:translateY(-4px)}}
@media(max-width:900px){.page-header{align-items:stretch;flex-direction:column}.header-tools{align-items:stretch}.knowledge-picker{width:100%}.workspace{grid-template-columns:1fr;height:auto}.chat-panel{min-height:620px}.citations-panel{max-height:500px}.conversation{padding:22px}.composer{margin:0 16px 16px}}
</style>
