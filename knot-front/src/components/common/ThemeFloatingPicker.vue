<template>
  <el-popover
    placement="top-end"
    trigger="click"
    width="220"
    popper-class="theme-floating-popover"
  >
    <template #reference>
      <button class="theme-fab" type="button" :aria-label="t('theme.pickerAriaLabel')">
        <span :class="['theme-fab__dot', 'theme-dot--' + current]" />
      </button>
    </template>

    <div class="theme-panel">
      <div class="theme-panel__title">{{ t("theme.pickerTitle") }}</div>
      <button
        v-for="theme in THEMES"
        :key="theme.key"
        :class="['theme-option', { active: current === theme.key }]"
        type="button"
        @click="setTheme(theme.key)"
      >
        <span :class="['theme-option__dot', 'theme-dot--' + theme.key]" />
        <span>{{ t(theme.labelKey) }}</span>
      </button>
    </div>
  </el-popover>
</template>

<script setup>
import { THEMES, useTheme } from "../../composables/useTheme";
import { useLocale } from "../../composables/useLocale";

const { current, setTheme } = useTheme();
const { t } = useLocale();
</script>

<style scoped>
.theme-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3000;
  width: 42px;
  height: 42px;
  border: 1px solid var(--knot-border, #ebeef5);
  border-radius: 50%;
  cursor: pointer;
  background: var(--knot-surface, #fff);
  box-shadow: 0 8px 20px rgba(31, 45, 61, 0.16);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.theme-fab:hover {
  transform: translateY(-1px);
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 10px 24px rgba(31, 45, 61, 0.2);
}

.theme-fab__dot {
  position: absolute;
  right: 7px;
  bottom: 7px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: 0 0 0 2px var(--knot-surface, #fff);
}

.theme-panel {
  display: grid;
  gap: 8px;
}

.theme-panel__title {
  margin-bottom: 2px;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.theme-option {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 0;
  color: #606266;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.16s ease, border-color 0.16s ease, color 0.16s ease;
}

.theme-option:hover,
.theme-option.active {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}

.theme-option__dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.65);
}

:global(.theme-floating-popover) {
  border-radius: 0;
  box-shadow: 0 14px 36px rgba(31, 45, 61, 0.16);
}

@media (max-width: 768px) {
  .theme-fab {
    right: 16px;
    bottom: 16px;
  }
}
</style>
