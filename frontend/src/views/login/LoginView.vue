<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkspaceStore } from '@/stores/workspace'

const workspaceId = ref('')
const router = useRouter()
const workspace = useWorkspaceStore()

function enter() {
  const id = workspaceId.value.trim()
  if (!id) {
    return
  }
  workspace.select(id)
  void router.push(`/workspaces/${id}/knowledge-bases`)
}
</script>

<template>
  <main class="login">
    <t-card title="进入 Workspace" class="card">
      <t-input v-model="workspaceId" placeholder="Workspace ID" @enter="enter" />
      <t-button class="action" theme="primary" @click="enter">进入</t-button>
    </t-card>
  </main>
</template>

<style scoped>
.login {
  display: grid;
  min-height: 100%;
  place-items: center;
}

.card {
  width: 360px;
}

.action {
  margin-top: 16px;
}
</style>
