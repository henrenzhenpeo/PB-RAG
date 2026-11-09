<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listKnowledgeBases, type KnowledgeBase } from '@/api/knowledgeBases'
import { useWorkspaceStore } from '@/stores/workspace'
import { listWorkspaces } from '@/api/workspaces'
import { currentUser, logout, type User } from '@/api/auth'
import { useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const user = ref<User | null>(null)
const workspace = useWorkspaceStore()
const workspaceId = computed(() => String(route.params.workspaceId ?? ''))
const knowledgeBases = ref<KnowledgeBase[]>([])
const activeKbId = computed(() => String(route.params.kbId ?? ''))

async function loadNavigation(id: string) {
  if (!id) return
  try {
    knowledgeBases.value = await listKnowledgeBases(id)
  } catch {
    knowledgeBases.value = []
  }
}

watch(
  workspaceId,
  (id) => {
    if (id) {
      workspace.select(id)
      void loadNavigation(id)
    }
  },
  { immediate: true },
)

onMounted(async () => {
  try { user.value = await currentUser() } catch { return }
  if (workspace.workspaceName || !workspaceId.value) return
  try {
    const current = (await listWorkspaces()).find((item) => item.id === workspaceId.value)
    if (current) workspace.select(current.id, current.name)
  } catch {
    // 子页面会展示具体的后端连接错误；布局保留可用。
  }
})

watch(() => route.fullPath, () => void loadNavigation(workspaceId.value))

async function signOut() { await logout(); void router.replace('/sign-in') }
</script>

<template>
  <t-layout class="platform">
    <t-aside class="sidebar" width="236px">
      <router-link to="/login" class="sidebar-brand"><span class="brand-mark">P</span><span><strong>PB-RAG</strong><small>KNOWLEDGE STUDIO</small></span></router-link>
      <nav class="nav">
        <span class="nav-caption">工作台</span>
        <router-link :to="`/workspaces/${workspaceId}/knowledge-bases`" class="nav-main"><span class="nav-icon">▦</span><span>知识库</span><span class="nav-count">{{ knowledgeBases.length }}</span></router-link>
        <div class="nav-sub">
          <router-link v-for="item in knowledgeBases" :key="item.id" :to="`/workspaces/${workspaceId}/knowledge-bases/${item.id}`" :class="{ selected: activeKbId === item.id }" :title="item.name"><span class="nav-dot"></span><span class="nav-name">{{ item.name }}</span></router-link>
          <span v-if="!knowledgeBases.length" class="nav-hint">暂无知识库</span>
        </div>
        <span class="nav-caption second">分析</span>
        <router-link :to="`/workspaces/${workspaceId}/evaluations`" class="nav-main"><span class="nav-icon">◫</span><span>评测</span></router-link>
      </nav>
      <div class="sidebar-bottom"><span class="bottom-label">当前工作区 · 所有账号共享</span><strong :title="workspace.workspaceName ?? workspaceId">{{ workspace.workspaceName ?? 'Workspace' }}</strong><router-link to="/login">切换工作区 ↗</router-link></div>
    </t-aside>
    <t-layout>
      <t-header class="header">
        <div>
          <strong>{{ workspace.workspaceName ?? 'Workspace' }}</strong>
          <span class="workspace-id">知识资源工作区</span>
        </div>
        <div class="header-actions"><router-link to="/login">所有工作区</router-link><router-link to="/profile">{{ user?.displayName ?? '个人资料' }}</router-link><button @click="signOut">退出</button></div>
      </t-header>
      <t-content class="content">
        <router-view />
      </t-content>
    </t-layout>
  </t-layout>
</template>

<style scoped>
.platform { min-height: 100%; }
.sidebar { display: flex; flex-direction: column; background: #fff; border-right: 1px solid #e7ecf3; }
.sidebar-brand { display: flex; align-items: center; gap: 10px; height: 76px; padding: 0 22px; color: #182338; text-decoration: none; border-bottom: 1px solid #edf0f6; }
.sidebar-brand > span:last-child { display: grid; gap: 2px; }.sidebar-brand strong { font-size: 16px; letter-spacing: .02em; }.sidebar-brand small { color: #93a0b4; font-size: 9px; font-weight: 700; letter-spacing: .11em; }
.brand-mark { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: #3159db; color: #fff; font-size: 18px; font-weight: 800; }

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  background: #fff;
  border-bottom: 1px solid var(--td-component-border);
}

.header > div { display: flex; flex-direction: column; gap: 2px; }
.workspace-id { font-size: 12px; color: var(--td-text-color-placeholder); }
.header a { color: var(--td-brand-color); text-decoration: none; }
.header-actions { display: flex; align-items: center; gap: 20px; font-size: 13px; }.header-actions button { border: 0; background: none; color: #718096; cursor: pointer; }

.nav { display: flex; flex-direction: column; gap: 4px; padding: 26px 12px; }
.nav-caption { padding: 4px 12px 8px; color: var(--td-text-color-placeholder); font-size: 12px; font-weight: 600; }
.nav-caption.second { margin-top: 24px; }
.nav-sub { display: grid; gap: 2px; padding-left: 14px; }
.nav-hint { padding: 8px 12px; color: var(--td-text-color-placeholder); font-size: 12px; }

.nav a {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  color: inherit;
  text-decoration: none;
  border-radius: var(--td-radius-medium);
}
.nav a:hover { background: var(--td-bg-color-container-hover); }
.nav-main { font-weight: 600; }.nav-icon { display: grid; place-items: center; width: 20px; color: #697d9f; font-size: 17px; }.nav-count { margin-left: auto; padding: 2px 7px; border-radius: 7px; background: #eef2f9; color: #71809a; font-size: 11px; }
.nav-sub a { min-width: 0; font-size: 13px; }.nav-name { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.nav-dot { flex: none; width: 6px; height: 6px; border-radius: 50%; background: #b8c5d8; }
.nav-sub a.selected { color: var(--td-brand-color); background: var(--td-brand-color-light); font-weight: 600; }
.nav-sub a.selected .nav-dot { background: var(--td-brand-color); }

.nav a.router-link-exact-active { color: var(--td-brand-color); background: var(--td-brand-color-light); }
.sidebar-bottom { display: grid; gap: 6px; margin: auto 12px 18px; padding: 16px; border-radius: 12px; background: #f4f7fd; }.bottom-label { color: #8c9ab0; font-size: 11px; }.sidebar-bottom strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }.sidebar-bottom a { color: #3159db; text-decoration: none; font-size: 12px; }

.content { padding: 32px; min-width: 0; }
@media (max-width: 760px) {
  .platform { display: block; }
  .sidebar { width: 100% !important; border-right: 0; border-bottom: 1px solid var(--td-component-border); }
  .sidebar-brand { height: 58px; }.sidebar-bottom { display: none; }
  .nav { padding: 10px 14px; }
  .nav-sub { display: flex; overflow-x: auto; padding-left: 0; }
  .nav-sub a { flex: none; max-width: 180px; }
  .nav-caption, .nav-caption.second { display: none; }
  .header { padding: 12px 20px; }
  .content { padding: 20px; }
}
</style>
