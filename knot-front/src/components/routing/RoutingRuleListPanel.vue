<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
      <el-table-column prop="ruleCode" label="规则编码" width="200" show-overflow-tooltip />
      <el-table-column label="消费者" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ consumerNamesLabel(row.consumerNames) }}
        </template>
      </el-table-column>
      <el-table-column prop="appName" label="应用" width="130" show-overflow-tooltip />
      <el-table-column prop="userName" label="用户" width="130" show-overflow-tooltip />
      <el-table-column label="路由目标" width="240">
        <template #default="{ row }">
          <div v-if="targetsLabel(row.targets).length" class="cell-targets">
            <span
              v-for="(label, index) in targetsLabel(row.targets)"
              :key="index"
              class="cell-targets__line"
            >{{ label }}</span>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="togglingId === row.id"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
            @change="(value) => handleEnabledChange(row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center" fixed="right" header-align="center">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'edit', label: '编辑', icon: Edit },
              { key: 'test', label: '测试', icon: VideoPlay },
              { key: 'log', label: '日志', icon: Document }
            ]"
            @action="(action) => handleAction(action, row)"
          />
        </template>
      </el-table-column>
    </el-table>

    <ListPagination
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      :show-refresh="showRefresh"
      @refresh="emit('refresh')"
      @page-change="(page) => emit('page-change', page)"
      @size-change="(size) => emit('size-change', size)"
    />
  </div>
</template>

<script setup lang="ts">
import {Document, Edit, VideoPlay} from "@element-plus/icons-vue";
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import {updateRoutingRuleStatus} from "@/api/routing";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {useEnumOptions} from "@/composables/useEnumOptions";
import type {PropType} from "vue";
import type {Row} from "@/types";

const { labelOf } = useEnumOptions();

defineProps({
  rows: { type: Array as PropType<Row[]>, default: (): Row[] => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits([
  "create",
  "refresh",
  "edit",
  "test",
  "log",
  "page-change",
  "size-change",
  "changed"
]);

const { togglingId, onEnabledChange } = useEnabledToggle({
  updateApi: updateRoutingRuleStatus
});

function targetsLabel(targets: unknown): string[] {
  if (!Array.isArray(targets) || !targets.length) {
    return [];
  }
  return targets.map((t: Row) => {
    const type = labelOf("RouteTargetTypeEnum", t.targetType, t.targetType);
    const name = t.targetName || t.targetCode || t.targetId || "";
    const code = t.targetCode || t.targetId || "";
    const text = [type, name].filter(Boolean).join("：") || String(code);
    return t.primary ? `${text}（主）` : text;
  });
}

function consumerNamesLabel(consumerNames: unknown) {
  return Array.isArray(consumerNames) && consumerNames.length ? consumerNames.join("、") : "-";
}

function handleAction(action: string, row: Row) {
  if (action === "edit") emit("edit", row);
  if (action === "test") emit("test", row);
  if (action === "log") emit("log", row);
}

async function handleEnabledChange(row: Row, enabled: string | number | boolean) {
  await onEnabledChange(row, enabled);
  emit("changed");
}
</script>

<style scoped>
.cell-targets {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 2px 0;
}

/* 路由目标每个目标独占一行，字号比常规单元格小一号 */
.cell-targets__line {
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
}
</style>
