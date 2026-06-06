<template>
  <PageSection title="规则列表">
    <ListPageHeader>
      <template #actions>
        <el-button type="primary" @click="openCreate">新建规则</el-button>
        <el-button @click="load">刷新</el-button>
      </template>
      <template #filters>
        <div class="list-filter-item list-filter-item--grow">
          <span class="list-filter-label">关键词</span>
          <el-input
            v-model="query.keyword"
            class="list-filter-control--wide"
            placeholder="按规则编码、名称、应用、用户筛选"
            clearable
            @keyup.enter="handleQuery"
          />
        </div>
        <div class="list-filter-item">
          <span class="list-filter-label">模型类型</span>
          <EnumSelect
            v-model="query.modelTypes"
            class="list-filter-control--wide"
            category="model_type"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
          />
        </div>
        <div class="list-filter-actions">
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </template>
    </ListPageHeader>

    <RoutingRuleListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @edit="openEdit"
      @test="openTest"
      @log="openChangeLog"
      @page-change="onPageChange"
      @size-change="onSizeChange"
      @changed="load"
    />

    <RoutingRuleFormDrawer v-model="formVisible" :rule="editingRule" @saved="onRuleSaved" />

    <RoutingRuleTestDrawer
      v-model="testVisible"
      :rule-id="testRuleId"
      :rule-name="testRuleName"
      :secret-key="testSecretKey"
      :targets="testTargets"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`路由规则变更日志 — ${logRuleName || ''}`"
      :load-logs="loadRoutingRuleOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import ListPageHeader from "../../components/common/ListPageHeader.vue";
import EnumSelect from "../../components/common/EnumSelect.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import RoutingRuleListPanel from "../../components/routing/RoutingRuleListPanel.vue";
import RoutingRuleFormDrawer from "../../components/routing/RoutingRuleFormDrawer.vue";
import RoutingRuleTestDrawer from "../../components/routing/RoutingRuleTestDrawer.vue";
import { listRoutingRuleOperationLogs } from "../../api/operationLogs";
import { usePageList } from "../../composables/usePageList";
import { listRoutingConsumers, listRoutingRules } from "../../api/routing";

const query = reactive({
  keyword: "",
  modelTypes: []
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listRoutingRules, { extra: query });

const formVisible = ref(false);
const editingRule = ref(null);
const testVisible = ref(false);
const testRuleId = ref(null);
const testRuleName = ref("");
const testSecretKey = ref("");
const testTargets = ref([]);
const logDrawer = ref(false);
const logRuleId = ref(null);
const logRuleName = ref("");

function openCreate() {
  editingRule.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingRule.value = row;
  formVisible.value = true;
}

function onRuleSaved() {
  resetPage();
}

async function openTest(row) {
  testRuleId.value = row.id;
  testRuleName.value = row.name || row.ruleCode || "";
  testSecretKey.value = await loadConsumerSecretKey(row.consumerIds);
  testTargets.value = Array.isArray(row.targets) ? row.targets : [];
  testVisible.value = true;
}

async function loadConsumerSecretKey(consumerIds) {
  const ids = Array.isArray(consumerIds) ? consumerIds : [];
  if (!ids.length) {
    return "";
  }
  const res = await listRoutingConsumers({ pageNum: 1, pageSize: 500 });
  const consumers = Array.isArray(res?.list) ? res.list : [];
  return consumers.find((item) => ids.includes(item.id))?.secretKey || "";
}

function openChangeLog(row) {
  logRuleId.value = row.id;
  logRuleName.value = row.name || row.ruleCode || `#${row.id}`;
  logDrawer.value = true;
}

function loadRoutingRuleOperationLogs() {
  return listRoutingRuleOperationLogs(logRuleId.value);
}

function handleQuery() {
  return resetPage();
}

function handleReset() {
  query.keyword = "";
  query.modelTypes = [];
  return resetPage();
}

load();
</script>
