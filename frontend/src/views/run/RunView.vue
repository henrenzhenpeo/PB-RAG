<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const runId = computed(() => String(route.params.runId ?? ''))
</script>

<template>
  <section class="split">
    <div>
      <h1>运行 {{ runId }}</h1>
    </div>
    <aside>
      <h2>工具、审批与取消</h2>
    </aside>
  </section>
</template>

<style scoped>
.split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
}

aside {
  border-left: 1px solid var(--td-component-border);
  padding-left: 16px;
}
</style>
