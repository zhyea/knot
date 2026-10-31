<template>
  <div class="dashboard-panel" :class="{'dashboard-panel--overview': overview}">
    <div class="panel-header">
      <div>
        <h2 class="panel-title">{{ title }}</h2>
        <p v-if="subtitle" class="panel-subtitle">{{ subtitle }}</p>
      </div>
      <!-- 右上角只有一个位置：给了 action 就用它，否则用 badge 文案 -->
      <slot name="action">
        <span v-if="badge" class="panel-badge">{{ badge }}</span>
      </slot>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps({
  title: {type: String, required: true},
  subtitle: {type: String, default: ""},
  /** 右上角徽标文案（如「模块 12」）；传了 action 插槽时它不显示 */
  badge: {type: String, default: ""},
  /** 资源概览那张卡要吃掉整列剩余高度，其余卡片按内容高度 */
  overview: {type: Boolean, default: false}
});
</script>

<style scoped>
.dashboard-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--knot-border, #ebeef5);
  background: #fff;
  padding: 18px 20px;
}

.dashboard-panel--overview {
  min-height: 0;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.panel-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.panel-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
}

.panel-badge {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  background: #f5f7fa;
  color: #606266;
  font-size: 13px;
}
</style>
