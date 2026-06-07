<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block list-page-block--content">
        <div class="settings-layout">
          <section class="settings-card">
            <div class="settings-card__header">
              <div>
                <h3>{{ t("settings.languageTitle") }}</h3>
                <p>{{ t("settings.languageDescription") }}</p>
              </div>
              <el-tag effect="plain">{{ t("common.current") }}: {{ currentLocaleLabel }}</el-tag>
            </div>

            <div class="settings-grid">
              <button
                v-for="locale in locales"
                :key="locale.code"
                :class="['settings-choice', { active: locale.code === currentLocale }]"
                type="button"
                :disabled="savingLocale"
                @click="chooseLocale(locale.code)"
              >
                <span class="settings-choice__label">{{ t(locale.labelKey) }}</span>
                <el-icon v-if="locale.code === currentLocale" class="settings-choice__check">
                  <Check />
                </el-icon>
              </button>
            </div>
          </section>

          <section class="settings-card">
            <div class="settings-card__header">
              <div>
                <h3>{{ t("settings.themeTitle") }}</h3>
                <p>{{ t("settings.themeDescription") }}</p>
              </div>
              <el-tag effect="plain">{{ t("common.current") }}: {{ currentThemeLabel }}</el-tag>
            </div>

            <div class="settings-grid">
              <button
                v-for="theme in THEMES"
                :key="theme.key"
                :class="['settings-choice', { active: currentTheme === theme.key }]"
                type="button"
                :disabled="savingTheme"
                @click="chooseTheme(theme.key)"
              >
                <span :class="['settings-choice__dot', 'theme-dot--' + theme.key]" />
                <span class="settings-choice__label">{{ t(theme.labelKey) }}</span>
                <el-icon v-if="currentTheme === theme.key" class="settings-choice__check">
                  <Check />
                </el-icon>
              </button>
            </div>
          </section>
        </div>
      </section>
    </div>
  </PageSection>
</template>

<script setup>
import { computed, ref } from "vue";
import { Check } from "@element-plus/icons-vue";
import PageSection from "../../components/common/PageSection.vue";
import { LOCALES, useLocale } from "../../composables/useLocale";
import { THEMES, useTheme } from "../../composables/useTheme";

const { current: localeCurrent, setLocale, t } = useLocale();
const { current: themeCurrent, setTheme } = useTheme();

const savingLocale = ref(false);
const savingTheme = ref(false);
const locales = LOCALES;

const currentLocale = computed(() => localeCurrent.value);
const currentTheme = computed(() => themeCurrent.value);
const currentLocaleLabel = computed(() => t(`locale.${currentLocale.value}`));
const currentThemeLabel = computed(() => t(THEMES.find((theme) => theme.key === currentTheme.value)?.labelKey || "theme.blue"));

async function chooseLocale(code) {
  if (code === currentLocale.value || savingLocale.value) {
    return;
  }
  savingLocale.value = true;
  try {
    await setLocale(code);
  } finally {
    savingLocale.value = false;
  }
}

async function chooseTheme(key) {
  if (key === currentTheme.value || savingTheme.value) {
    return;
  }
  savingTheme.value = true;
  try {
    await setTheme(key);
  } finally {
    savingTheme.value = false;
  }
}
</script>

<style scoped>
.settings-layout {
  display: grid;
  gap: 16px;
  max-width: 880px;
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

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.settings-choice {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid var(--knot-border, #dcdfe6);
  color: #606266;
  background: var(--knot-surface, #fff);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.16s ease, color 0.16s ease, background 0.16s ease;
}

.settings-choice:hover,
.settings-choice.active {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.settings-choice:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.settings-choice__dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.65);
}

.settings-choice__label {
  flex: 1;
  font-size: 14px;
}

.settings-choice__check {
  color: var(--el-color-primary);
}
</style>
