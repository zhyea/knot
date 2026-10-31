<template>
  <section class="dashboard-hero">
    <div class="dashboard-hero__copy">
      <p class="dashboard-hero__eyebrow">Knot AI Gateway</p>
      <h1 class="dashboard-hero__title">控制台总览</h1>
      <p class="dashboard-hero__desc">
        统一查看应用接入、模型资源、路由配置和最近操作，减少在多个页面之间来回切换。
      </p>
    </div>
    <div class="dashboard-hero__actions">
      <div class="refresh-pill">
        <strong class="refresh-pill__value">{{ updatedAtText }}</strong>
      </div>
      <el-button
        class="refresh-button"
        :icon="RefreshRight"
        aria-label="刷新看板数据"
        type="primary"
        plain
        @click="emit('refresh')"
      ></el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import {RefreshRight} from "@element-plus/icons-vue";

defineProps({
  /** 已格式化的「最近更新时间」；格式化在宿主做（它才知道时区），本组件保持无状态 */
  updatedAtText: {type: String, required: true}
});

const emit = defineEmits(["refresh"]);
</script>

<style scoped>
.dashboard-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-hero__copy {
  min-width: 0;
}

.dashboard-hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  color: #909399;
  text-transform: uppercase;
}

.dashboard-hero__title {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  color: #1f2937;
}

.dashboard-hero__desc {
  margin: 10px 0 0;
  max-width: 720px;
  font-size: var(--el-font-size-base, 13px);
  line-height: 1.7;
  color: #606266;
}

.dashboard-hero__actions {
  --refresh-control-width: 200px;
  --refresh-control-height: 36px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.refresh-pill {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  width: var(--refresh-control-width);
  min-width: var(--refresh-control-width);
  height: var(--refresh-control-height);
  padding: 10px 14px;
  border: 1px solid var(--knot-border, #ebeef5);
  background: #fff;
}

.refresh-button {
  width: var(--refresh-control-height);
  min-width: var(--refresh-control-height);
  height: var(--refresh-control-height);
  padding: 0;
}

.refresh-pill__value {
  font-size: var(--el-font-size-base, 13px);
  color: #303133;
}

@media (max-width: 768px) {
  .dashboard-hero,
  .dashboard-hero__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .refresh-pill {
    width: 100%;
    min-width: 0;
  }

  .refresh-button {
    width: 36px;
  }
}
</style>
