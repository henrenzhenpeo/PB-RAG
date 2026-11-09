<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { MessagePlugin } from 'tdesign-vue-next'
import { useWorkspaceStore } from '@/stores/workspace'
import { createWorkspace, listWorkspaces, type Workspace } from '@/api/workspaces'
import { errorMessage } from '@/api/http'
import { currentUser, logout, type User } from '@/api/auth'

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const user = ref<User | null>(null)
const workspaces = ref<Workspace[]>([])
const loading = ref(false)
const creating = ref(false)
const createVisible = ref(false)
const name = ref('')

async function load() {
  loading.value = true
  try {
    user.value = await currentUser()
    workspaces.value = await listWorkspaces()
  } catch (error) {
    if (!user.value) void router.replace('/sign-in')
    else void MessagePlugin.error(errorMessage(error))
  } finally { loading.value = false }
}

function enter(workspace: Workspace) {
  workspaceStore.select(workspace.id, workspace.name)
  void router.push(`/workspaces/${workspace.id}/knowledge-bases`)
}

async function submitCreate() {
  const normalizedName = name.value.trim()
  if (!normalizedName) return
  creating.value = true
  try {
    const workspace = await createWorkspace(normalizedName)
    workspaces.value.push(workspace)
    createVisible.value = false
    name.value = ''
    enter(workspace)
  } catch (error) { void MessagePlugin.error(errorMessage(error)) }
  finally { creating.value = false }
}

async function signOut() {
  await logout()
  void router.replace('/sign-in')
}

onMounted(load)
</script>

<template>
  <main class="home">
    <div class="shell">
      <header class="topbar"><strong>PB-RAG</strong><div v-if="user" class="account"><span>{{ user.displayName }}</span><router-link to="/profile">个人资料</router-link><button @click="signOut">退出</button></div></header>
      <div class="heading"><div><h1>工作区</h1><p>选择工作区，管理知识库和文档。当前工作区对所有已登录用户共享。</p></div><t-button theme="primary" @click="createVisible = true">新建工作区</t-button></div>
      <t-loading :loading="loading"><div v-if="workspaces.length" class="workspace-list"><button v-for="item in workspaces" :key="item.id" class="workspace" @click="enter(item)"><span class="workspace-name">{{ item.name }}</span><span class="workspace-next">进入 →</span></button></div><div v-else-if="!loading" class="empty"><p>还没有工作区</p><t-button variant="outline" @click="createVisible = true">创建工作区</t-button></div></t-loading>
    </div>
    <t-dialog v-model:visible="createVisible" header="新建工作区" :confirm-btn="{ content: '创建', loading: creating }" @confirm="submitCreate"><t-input v-model="name" :maxlength="120" placeholder="工作区名称" @enter="submitCreate" /></t-dialog>
  </main>
</template>

<style scoped>
.home { min-height: 100%; background: #f7f8fa; }.shell { max-width: 1040px; margin: auto; padding: 0 24px 64px; }
.topbar { display: flex; align-items: center; justify-content: space-between; height: 68px; border-bottom: 1px solid #e8ebf0; }.topbar strong { font-size: 18px; }.account { display: flex; align-items: center; gap: 18px; font-size: 13px; }.account a, .account button { color: #3159db; text-decoration: none; }.account button { border: 0; background: none; cursor: pointer; }
.heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin: 54px 0 24px; }.heading h1 { margin: 0 0 8px; font-size: 28px; }.heading p { margin: 0; color: #718096; font-size: 14px; }
.workspace-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }.workspace { display: flex; align-items: center; justify-content: space-between; min-height: 104px; padding: 22px; text-align: left; cursor: pointer; border: 1px solid #e1e5eb; border-radius: 10px; background: #fff; }.workspace:hover { border-color: #3159db; }.workspace-name { font-size: 16px; font-weight: 600; }.workspace-next { color: #3159db; font-size: 13px; }.empty { padding: 48px; text-align: center; border: 1px dashed #ccd3de; border-radius: 10px; background: #fff; }.empty p { color: #718096; }
@media (max-width: 600px) { .heading { align-items: stretch; flex-direction: column; margin-top: 36px; }.account { gap: 10px; } }
</style>
