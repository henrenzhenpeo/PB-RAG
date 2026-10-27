<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const workspaceId = computed(() => String(route.params.workspaceId ?? ''))
</script>

<template>
  <section>
    <h1>知识库</h1>
    <p>Workspace {{ workspaceId }}</p>
  </section>
</template>
