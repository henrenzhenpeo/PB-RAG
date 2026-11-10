<script setup lang="ts">
// 对话详情页占位骨架：绑定路由 chatId，为后续 SSE 回答与引用展示保留入口。
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const chatId = computed(() => String(route.params.chatId ?? ''))
</script>

<template>
  <section class="split">
    <div>
      <h1>对话 {{ chatId }}</h1>
    </div>
    <aside>
      <h2>引用与检索阶段</h2>
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
