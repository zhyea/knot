<template>
  <DashboardPanel title="快捷入口" subtitle="优先展示你当前可访问的页面。">
    <div class="quick-links">
      <button
        v-for="link in links"
        :key="link.path"
        class="quick-links__item"
        type="button"
        @click="emit('select', link.path)"
      >
        <el-icon class="quick-links__icon"><component :is="link.icon" /></el-icon>
        <span class="quick-links__text">{{ link.label }}</span>
      </button>
      <el-empty v-if="!links.length" description="暂无可访问入口" :image-size="72" />
    </div>
  </DashboardPanel>
</template>

<script setup lang="ts">
import type {PropType} from "vue";
import type {QuickLink} from "@/utils/dashboardQuickLinks";
import DashboardPanel from "./DashboardPanel.vue";

defineProps({
  /** 已去重并截断的入口列表（收集规则在 utils/dashboardQuickLinks） */
  links: {type: Array as PropType<QuickLink[]>, required: true}
});

const emit = defineEmits(["select"]);
</script>

<style scoped>
.quick-links {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

.quick-links__item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 0 14px;
  border: 1px solid #e8eef5;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.quick-links__item:hover {
  border-color: #bfd3eb;
  background: #f8fbff;
}

.quick-links__icon {
  flex: 0 0 auto;
  font-size: 16px;
  color: #4a94e8;
}

.quick-links__text {
  min-width: 0;
  font-size: 13px;
  color: #303133;
}

@media (max-width: 768px) {
  .quick-links {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
