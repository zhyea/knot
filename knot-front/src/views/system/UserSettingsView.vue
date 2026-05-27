<template>
  <PageSection title="用户设置">
    <div class="settings-layout">
      <section class="settings-card">
        <div class="settings-card__header">
          <div>
            <h3>页面主题</h3>
            <p>主题会保存到当前登录用户，下次登录后自动恢复。</p>
          </div>
          <el-tag effect="plain">当前：{{ currentThemeLabel }}</el-tag>
        </div>

        <div class="theme-grid">
          <button
            v-for="theme in THEMES"
            :key="theme.key"
            :class="['theme-choice', { active: current === theme.key }]"
            type="button"
            :disabled="saving"
            @click="chooseTheme(theme.key)"
          >
            <span :class="['theme-choice__dot', 'theme-dot--' + theme.key]" />
            <span class="theme-choice__label">{{ theme.label }}</span>
            <el-icon v-if="current === theme.key" class="theme-choice__check">
              <Check />
            </el-icon>
          </button>
        </div>
      </section>
    </div>
  </PageSection>
</template>

<script setup>
import { computed, ref } from "vue";
import { Check } from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import { THEMES, useTheme } from "../../composables/useTheme";

const { current, setTheme } = useTheme();
const saving = ref(false);

const currentThemeLabel = computed(() => {
  return THEMES.find((theme) => theme.key === current.value)?.label || "默认";
});

async function chooseTheme(key) {
  if (key === current.value || saving.value) {
    return;
  }
  saving.value = true;
  try {
    await setTheme(key);
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.settings-layout {
  max-width: 760px;
}

.settings-card {
  padding: 18px;
  border: 1px solid var(--knot-border, #dcdfe6);
  background: var(--knot-surface-soft, #f8fafc);
}

.settings-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.settings-card__header h3 {
  margin: 0 0 6px;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.settings-card__header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.theme-choice {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--knot-border, #dcdfe6);
  border-radius: 0;
  color: #606266;
  background: var(--knot-surface, #fff);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.16s ease, color 0.16s ease, background 0.16s ease;
}

.theme-choice:hover,
.theme-choice.active {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.theme-choice:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.theme-choice__dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.65);
}

.theme-choice__label {
  flex: 1;
  font-size: 14px;
}

.theme-choice__check {
  color: var(--el-color-primary);
}
</style>
