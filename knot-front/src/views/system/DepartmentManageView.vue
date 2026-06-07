<template>
  <PageSection>
    <div class="department-layout">
      <aside class="department-tree">
        <div class="tree-toolbar">
          <el-input v-model="treeKeyword" placeholder="输入名称过滤" clearable />
        </div>
        <el-scrollbar class="tree-scrollbar">
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            :filter-node-method="filterTreeNode"
            @node-click="onTreeNodeClick"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span class="tree-node__name">{{ data.deptName }}</span>
                <span class="tree-node__code">{{ data.deptCode }}</span>
              </div>
            </template>
          </el-tree>
        </el-scrollbar>
      </aside>

      <section class="department-content">
        <DepartmentListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :selected-node="selectedNode"
          :create-label="selectedNode ? '新建同级部门' : '新建一级部门'"
          @create="openCreate"
          @create-child="openCreateChild"
          @refresh="refreshAll"
          @status-change="onStatusChange"
          @action="handleAction"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <DepartmentFormDrawer
      v-model="drawerVisible"
      :department="editingDepartment"
      :tree-options="treeData"
      :default-parent-id="defaultParentId"
      @saved="onSaved"
    />

    <OperationLogDrawer
      v-model="logDrawerVisible"
      :title="logDrawerTitle"
      :load-logs="loadDepartmentOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import DepartmentFormDrawer from "../../components/system/DepartmentFormDrawer.vue";
import DepartmentListPanel from "../../components/system/DepartmentListPanel.vue";
import { usePageList } from "../../composables/usePageList";
import { deleteDepartment, getDepartmentTree, listDepartments, updateDepartmentStatus } from "../../api/departments";
import { listDepartmentOperationLogs } from "../../api/operationLogs";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage, extra } =
  usePageList(listDepartments, { extra: { parentId: 0 } });

const drawerVisible = ref(false);
const editingDepartment = ref(null);
const defaultParentId = ref(null);
const treeData = ref([]);
const treeRef = ref(null);
const treeKeyword = ref("");
const selectedNode = ref(null);

const logDrawerVisible = ref(false);
const logDepartmentId = ref(null);
const logDrawerTitle = ref("操作日志");

watch(treeKeyword, (value) => {
  treeRef.value?.filter(value);
});

const flatTreeMap = computed(() => {
  const map = new Map();
  const walk = (nodes, level = 1) => {
    for (const node of nodes || []) {
      map.set(node.id, { ...node, level });
      walk(node.children, level + 1);
    }
  };
  walk(treeData.value);
  return map;
});

function openCreate() {
  editingDepartment.value = null;
  defaultParentId.value = selectedNode.value?.parentId ?? null;
  drawerVisible.value = true;
}

function openCreateChild() {
  if (!selectedNode.value) {
    return;
  }
  editingDepartment.value = null;
  defaultParentId.value = selectedNode.value.id;
  drawerVisible.value = true;
}

function openEdit(row) {
  editingDepartment.value = {
    ...row,
    children: flatTreeMap.value.get(row.id)?.children || []
  };
  defaultParentId.value = row.parentId ?? null;
  drawerVisible.value = true;
}

function openDepartmentLogs(row) {
  logDepartmentId.value = row.id;
  logDrawerTitle.value = `部门操作日志 — ${row.deptName || row.id}`;
  logDrawerVisible.value = true;
}

function loadDepartmentOperationLogs() {
  if (logDepartmentId.value == null) {
    return Promise.resolve([]);
  }
  return listDepartmentOperationLogs(logDepartmentId.value);
}

function handleAction(action, row) {
  if (action === "edit") openEdit(row);
  if (action === "log") openDepartmentLogs(row);
  if (action === "delete") onDelete(row);
}

async function onStatusChange(row, status) {
  try {
    await updateDepartmentStatus(row.id, { status });
    ElMessage.success("状态已更新");
    await refreshAll();
  } catch {
    row.status = status === 1 ? 0 : 1;
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除部门「${row.deptName || row.deptCode}」？`, "删除确认", { type: "warning" });
  await deleteDepartment(row.id);
  ElMessage.success("已删除");
  await refreshAll();
}

function filterTreeNode(keyword, data) {
  if (!keyword) return true;
  const value = keyword.trim().toLowerCase();
  return `${data.deptName || ""} ${data.deptCode || ""}`.toLowerCase().includes(value);
}

function onTreeNodeClick(data) {
  selectedNode.value = data;
  extra.parentId = data.id;
  resetPage();
}

function annotateLevels(nodes, level = 1) {
  return (nodes || []).map((node) => ({
    ...node,
    level,
    children: annotateLevels(node.children, level + 1)
  }));
}

function withRowLevels(list) {
  return (list || []).map((row) => ({
    ...row,
    level: flatTreeMap.value.get(row.id)?.level || 1
  }));
}

async function loadTree() {
  const data = await getDepartmentTree();
  treeData.value = annotateLevels(data);
}

async function refreshList() {
  await load();
  rows.value = withRowLevels(rows.value);
}

async function refreshAll() {
  await loadTree();
  await refreshList();
}

async function onSaved() {
  await refreshAll();
}

refreshAll();
</script>

<style scoped>
.department-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  min-height: 640px;
}

.department-tree,
.department-content {
  min-width: 0;
  background: var(--knot-surface, #fff);
  border: 1px solid var(--knot-border, #ebeef5);
}

.department-tree {
  display: flex;
  flex-direction: column;
}

.tree-toolbar {
  padding: 12px;
  border-bottom: 1px solid var(--knot-border, #ebeef5);
}

.tree-scrollbar {
  flex: 1;
  min-height: 0;
}

.tree-scrollbar :deep(.el-scrollbar__view) {
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.tree-node__name,
.tree-node__code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node__name {
  color: #303133;
}

.tree-node__code {
  color: #909399;
  font-size: 12px;
}

.department-content {
  padding: 16px;
}
</style>
