<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="emit('create')">新建规则</el-button>
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="70" align="center" header-align="center"/>
      <el-table-column prop="ruleCode" label="规则编码" min-width="120" show-overflow-tooltip/>
      <el-table-column prop="name" label="名称" min-width="120"/>
      <el-table-column label="消费者" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ consumerNamesLabel(row.consumerNames) }}
        </template>
      </el-table-column>
      <el-table-column prop="appName" label="应用" min-width="100" show-overflow-tooltip/>
      <el-table-column prop="userName" label="用户" min-width="100" show-overflow-tooltip/>
      <el-table-column label="模型类型" min-width="88" show-overflow-tooltip>
        <template #default="{ row }">
          {{ modelTypesLabel(row.modelTypes) }}
        </template>
      </el-table-column>
      <el-table-column label="启用" width="88" align="center">
        <template #default="{ row }">
          <el-switch
              :model-value="row.enabled !== false"
              :loading="togglingId === row.id"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              @change="(val) => handleEnabledChange(row, val)"
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
    <div class="pagination-wrap">
      <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          :page-sizes="[10, 20, 50]"
          @current-change="(p) => emit('page-change', p)"
          @size-change="(s) => emit('size-change', s)"
      />
    </div>
  </div>
</template>

<script setup>
import {onMounted} from "vue";
import {Document, Edit, VideoPlay} from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";
import {updateRoutingRule} from "../../api/routing";
import {useEnabledToggle} from "../../composables/useEnabledToggle";
import {useEnums, resolveEnumLabel} from "../../composables/useEnums";
import {buildRoutingRulePayload} from "../../utils/routingRule";

const {options: modelTypeOptions, loadOptions: loadModelTypeOptions} = useEnums("model_type");

function modelTypesLabel(modelTypes) {
  const list = Array.isArray(modelTypes) && modelTypes.length ? modelTypes : ["CHAT"];
  return list.map((code) => resolveEnumLabel(modelTypeOptions.value, code, code)).join("、");
}

function consumerNamesLabel(consumerNames) {
  return Array.isArray(consumerNames) && consumerNames.length ? consumerNames.join("、") : "—";
}

function targetTypeLabel(type) {
  return type === "MODEL_POOL" ? "模型池" : "模型";
}

onMounted(() => {
  loadModelTypeOptions();
});

defineProps({
  rows: {type: Array, default: () => []},
  loading: {type: Boolean, default: false},
  total: {type: Number, default: 0},
  pageNum: {type: Number, default: 1},
  pageSize: {type: Number, default: 20}
});

const emit = defineEmits(["create", "refresh", "edit", "test", "log", "page-change", "size-change", "changed"]);

const {togglingId, onEnabledChange} = useEnabledToggle({
  updateApi: updateRoutingRule,
  buildPayload: buildRoutingRulePayload
});

function handleAction(action, row) {
  if (action === "edit") emit("edit", row);
  if (action === "test") emit("test", row);
  if (action === "log") emit("log", row);
}

async function handleEnabledChange(row, enabled) {
  await onEnabledChange(row, enabled);
  emit("changed");
}
</script>

<style scoped>
.model-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.priority-hint {
  margin-left: 4px;
  opacity: 0.75;
}
</style>
