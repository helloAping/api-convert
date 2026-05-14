<script setup lang="ts">
</script>

<template>
  <n-message-provider>
    <router-view />
  </n-message-provider>
</template>

<style>
html,
body,
#app {
  width: 100%;
  height: 100%;
  margin: 0;
}

body {
  overflow: hidden;
}
</style>
