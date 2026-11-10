<script setup lang="ts">
// 根组件只承载路由出口，业务状态由布局、页面和 Pinia Store 管理。
</script>

<template>
  <router-view />
</template>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
}

body {
  color: var(--td-text-color-primary);
  background: var(--td-bg-color-page);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}

* { box-sizing: border-box; }
</style>
