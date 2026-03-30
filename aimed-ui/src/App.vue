<template>
  <component :is="currentPage" />
</template>

<script setup>
import { computed } from 'vue'
import ChatWindow from '@/components/ChatWindow.vue'
import KnowledgeCenterPage from '@/components/KnowledgeCenterPage.vue'

const currentPage = computed(() =>
  window.location.pathname.startsWith('/knowledge') ? KnowledgeCenterPage : ChatWindow
)
</script>

<style>
html,
body,
#app {
  height: 100%;
}

body {
  overflow: hidden;
  margin: 0;
}
</style>
