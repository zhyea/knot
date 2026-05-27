<template>
  <component :is="layout">
    <router-view />
  </component>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import MainLayout from './layouts/MainLayout.vue';
import { loadThemePreference } from './composables/useTheme';

const route = useRoute();

// 根据路由动态选择布局
const layout = computed(() => {
  // 登录页不需要MainLayout
  if (route.path === '/login') {
    return 'div';
  }
  return MainLayout;
});

onMounted(() => {
  loadThemePreference();
});
</script>
