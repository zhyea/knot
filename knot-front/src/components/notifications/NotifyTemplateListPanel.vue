<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="channel" label="渠道" width="90" />
      <el-table-column prop="content" label="内容模板" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <RowActions
            :actions="[{ key: 'log', label: '操作日志', icon: Document }]"
            @action="(action) => emit('action', action, row)"
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
import ListPagination from "../common/ListPagination.vue";
import RowActions from "../common/RowActions.vue";
import { Document } from "@element-plus/icons-vue";
import type {PropType} from "vue";
import type {Row} from "@/types";

defineProps({
  rows: { type: Array as PropType<Row[]>, default: (): Row[] => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  showRefresh: { type: Boolean, default: true }
});

const emit = defineEmits(["create", "refresh", "action", "page-change", "size-change"]);
</script>
