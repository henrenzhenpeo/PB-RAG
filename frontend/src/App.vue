<script setup lang="ts"></script>

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
