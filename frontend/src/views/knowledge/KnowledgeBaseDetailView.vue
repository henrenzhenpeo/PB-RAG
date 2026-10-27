<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const kbId = computed(() => String(route.params.kbId ?? ''))
</script>

<template>
  <section>
    <h1>知识库 {{ kbId }}</h1>
    <p>上传、解析阶段、失败原因和重试会接在这里。</p>
  </section>
</template>
