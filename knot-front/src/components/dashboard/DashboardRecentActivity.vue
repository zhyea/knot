<template>
  <DashboardPanel title="最近操作" subtitle="展示最新的操作记录摘要。">
    <template #action>
      <el-button link type="primary" @click="emit('open-all')">查看全部</el-button>
    </template>

    <div v-if="logs.length" class="activity-list">
      <button
        v-for="log in logs"
        :key="log.id"
        class="activity-list__item"
        type="button"
        @click="emit('open-all')"
      >
        <span class="activity-list__main">
          <span class="activity-list__title">
            <span class="activity-list__module">{{ resolveModuleName(log.module, moduleNameMap) }}</span>
            <span class="activity-list__operation">{{ displayText(log.operation) }}</span>
          </span>
          <span class="activity-list__entity">{{ displayText(log.entityName || log.entityType) }}</span>
        </span>
        <span class="activity-list__meta">
          <el-tag :type="log.status === OperationLogStatus.SUCCESS ? 'success' : 'danger'" size="small">
            {{ displayText(log.status) }}
          </el-tag>
          <span class="activity-list__time">{{ formatDateTime(log.createdAt) }}</span>
        </span>
      </button>
    </div>
    <el-empty v-else description="暂无操作记录" :image-size="72" />
  </DashboardPanel>
</template>

<script setup lang="ts">
import type {PropType} from "vue";
import {OperationLogStatus} from "@/constants/status";
import {displayText, formatDateTime} from "@/utils/format";
import {resolveModuleName} from "@/utils/dashboardModules";
import type {Row} from "@/types";
import DashboardPanel from "./DashboardPanel.vue";

defineProps({
  logs: {type: Array as PropType<Row[]>, required: true},
  /** 模块 code → 中文名（接口下发的那份，见 utils/dashboardModules.buildModuleNameMap） */
  moduleNameMap: {type: Object as PropType<Record<string, string>>, required: true}
});

const emit = defineEmits(["open-all"]);
</script>

<style scoped>
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.activity-list__item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border: none;
  border-top: 1px solid var(--knot-border, #ebeef5);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.activity-list__item:first-child {
  padding-top: 0;
  border-top: none;
}

.activity-list__main,
.activity-list__meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.activity-list__main {
  flex: 1;
}

.activity-list__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.activity-list__module {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.activity-list__operation,
.activity-list__entity,
.activity-list__time {
  font-size: 12px;
  color: #909399;
}

.activity-list__meta {
  align-items: flex-end;
}

@media (max-width: 768px) {
  .activity-list__item {
    flex-direction: column;
  }

  .activity-list__meta {
    align-items: flex-start;
  }
}
</style>
