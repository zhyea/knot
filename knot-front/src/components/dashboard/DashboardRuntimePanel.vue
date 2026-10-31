<template>
  <DashboardPanel title="运行状态" subtitle="查看服务和数据源的当前时间戳。">
    <div class="status-hero">
      <span class="status-hero__label">服务状态</span>
      <span
        class="status-hero__value"
        :class="{
          'status-hero__value--up': isHealthUp,
          'status-hero__value--down': isHealthDown
        }"
      >
        {{ displayText(status) }}
      </span>
    </div>

    <div class="status-list">
      <div class="status-list__item">
        <span class="status-list__label">服务时间</span>
        <strong class="status-list__value">{{ displayText(serverTime) }}</strong>
      </div>
      <div class="status-list__item">
        <span class="status-list__label">数据库时间</span>
        <strong class="status-list__value">{{ displayText(dbTime) }}</strong>
      </div>
      <div class="status-list__item">
        <span class="status-list__label">模块目录</span>
        <strong class="status-list__value">{{ formatThousands(moduleCount) }}</strong>
      </div>
    </div>
  </DashboardPanel>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {displayText, formatThousands} from "@/utils/format";
import DashboardPanel from "./DashboardPanel.vue";

const props = defineProps({
  /** 健康检查返回的状态词（UP/OK 视为正常，其余非空视为异常，空 = 没取到） */
  status: {type: String, default: ""},
  serverTime: {type: String, default: ""},
  dbTime: {type: String, default: ""},
  moduleCount: {type: Number, default: 0}
});

const isHealthUp = computed(() => ["UP", "OK"].includes(`${props.status}`.toUpperCase()));
/** 取不到状态（空串）既不算 up 也不算 down：显示默认色，不谎报故障 */
const isHealthDown = computed(() => !!props.status && !isHealthUp.value);
</script>

<style scoped>
.status-hero {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  background: #f8fafc;
}

.status-hero__label {
  font-size: 13px;
  color: #909399;
}

.status-hero__value {
  font-size: 28px;
  font-weight: 700;
  color: #d97706;
}

.status-hero__value--up {
  color: #16a34a;
}

.status-hero__value--down {
  color: #dc2626;
}

.status-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.status-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.status-list__label {
  font-size: 13px;
  color: #909399;
}

.status-list__value {
  font-size: 13px;
  color: #303133;
  text-align: right;
}
</style>
