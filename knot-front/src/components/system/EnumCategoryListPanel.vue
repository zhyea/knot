<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="emit('create-category')">新增分类首项</el-button>
      <el-button @click="emit('refresh')">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="summaries" stripe border size="small">
      <el-table-column prop="category" label="分类编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类名称" min-width="120" show-overflow-tooltip />
      <el-table-column label="系统" width="72" align="center">
        <template #default="{ row }">
          <StatusTag :active="row.isSystem" />
        </template>
      </el-table-column>
      <el-table-column prop="itemCount" label="枚举项数" width="110" align="center" />
      <el-table-column prop="enabledCount" label="已启用" width="100" align="center" />
      <el-table-column label="操作" width="110" align="center" header-align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[
              { key: 'items', label: '枚举项', icon: List },
              { key: 'log', label: '日志', icon: Document }
            ]"
            @action="(action) => emit('action', action, row)"
          />
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && summaries.length === 0"
      description="暂无枚举分类。请执行库表初始化（enum_categories / enum_configs）或点击「新增分类首项」创建。"
    />
  </div>
</template>

<script setup>
import { Document, List } from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";
import StatusTag from "../common/StatusTag.vue";

defineProps({
  summaries: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
});

const emit = defineEmits(["create-category", "refresh", "action"]);
</script>
