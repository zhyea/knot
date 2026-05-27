<template>
  <PageSection title="规则列表">
    <RoutingRuleListPanel
      :rows="rows"
      :loading="loading"
      :total="total"
      :page-num="pageNum"
      :page-size="pageSize"
      @create="openCreate"
      @refresh="load"
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
      :models="testModels"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`路由规则变更日志 — ${logRuleName || ''}`"
      :load-logs="loadRoutingRuleOperationLogs"
    />
  </PageSection>
</template>

<script setup>
import { ref } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import RoutingRuleListPanel from "../../components/routing/RoutingRuleListPanel.vue";
import RoutingRuleFormDrawer from "../../components/routing/RoutingRuleFormDrawer.vue";
import RoutingRuleTestDrawer from "../../components/routing/RoutingRuleTestDrawer.vue";
import { listRoutingRuleOperationLogs } from "../../api/operationLogs";
import { usePageList } from "../../composables/usePageList";
import { listRoutingConsumers, listRoutingRules } from "../../api/routing";

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listRoutingRules);

const formVisible = ref(false);
const editingRule = ref(null);

const testVisible = ref(false);
const testRuleId = ref(null);
const testRuleName = ref("");
const testSecretKey = ref("");
const testModels = ref([]);

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
  testModels.value = Array.isArray(row.models) ? row.models : [];
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

load();
</script>
