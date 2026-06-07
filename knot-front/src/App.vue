<template>
  <el-config-provider :locale="elementLocale">
    <component :is="layout">
      <router-view />
    </component>
  </el-config-provider>
</template>

<script setup>
import { computed, onMounted, watch } from "vue";
import { useRoute } from "vue-router";
import MainLayout from "./layouts/MainLayout.vue";
import { useLocale } from "./composables/useLocale";
import { loadThemePreference } from "./composables/useTheme";

const route = useRoute();
const { elementLocale, loadLocalePreference, t } = useLocale();

const layout = computed(() => (route.path === "/login" ? "div" : MainLayout));

const pageTitle = computed(() => {
  if (route.meta?.titleKey) {
    return t(route.meta.titleKey);
  }
  return route.meta?.title || t("app.title");
});

watch(
  pageTitle,
  (value) => {
    const appTitle = t("app.title");
    document.title = value === appTitle ? value : `${value} - ${appTitle}`;
  },
  { immediate: true }
);

onMounted(() => {
  loadThemePreference();
  loadLocalePreference();
});
</script>
