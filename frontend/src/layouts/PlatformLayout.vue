<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const workspace = useWorkspaceStore()
const workspaceId = computed(() => String(route.params.workspaceId ?? ''))

watch(
  workspaceId,
  (id) => {
    if (id) {
      workspace.select(id)
    }
  },
  { immediate: true },
)

const items = computed(() => [
  {
    value: 'knowledge-bases',
    label: '知识库',
    to: `/workspaces/${workspaceId.value}/knowledge-bases`,
  },
  {
    value: 'evaluations',
    label: '评测',
    to: `/workspaces/${workspaceId.value}/evaluations`,
  },
])
</script>

<template>
  <t-layout class="platform">
    <t-aside width="200px">
      <nav class="nav">
        <router-link v-for="item in items" :key="item.value" :to="item.to">
          {{ item.label }}
        </router-link>
      </nav>
    </t-aside>
    <t-layout>
      <t-header class="header">Workspace {{ workspaceId }}</t-header>
      <t-content class="content">
        <router-view />
      </t-content>
    </t-layout>
  </t-layout>
</template>

<style scoped>
.platform {
  min-height: 100%;
}

.header {
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid var(--td-component-border);
}

.nav {
  display: grid;
  gap: 8px;
  padding: 16px;
}

.nav a {
  color: inherit;
  text-decoration: none;
}

.content {
  padding: 16px;
}
</style>
