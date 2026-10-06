<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按规则编码、名称、应用、用户筛选"
          @query="handleQuery"
        />
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建规则</el-button>
          </div>
        </div>

        <RoutingRuleListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :show-refresh="false"
          @edit="openEdit"
          @test="openTest"
          @log="openChangeLog"
          @page-change="onPageChange"
          @size-change="onSizeChange"
          @changed="load"
        />
      </section>
    </div>

    <RoutingRuleFormDrawer v-model="formVisible" :rule="editingRule" @saved="onRuleSaved" />

    <RoutingRuleTestDrawer
      v-model="testVisible"
      :rule-id="testRuleId"
      :rule-name="testRuleName"
      :rule-code="testRuleCode"
      :secret-key="testSecretKey"
      :targets="testTargets"
    />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`路由规则变更日志 - ${logRuleName || ''}`"
      :load-logs="loadRoutingRuleOperationLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Dict, Row} from "@/types";
import {ref} from "vue";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import {useListQuery} from "@/composables/useListQuery";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import RoutingRuleListPanel from "../../components/routing/RoutingRuleListPanel.vue";
import RoutingRuleFormDrawer from "../../components/routing/RoutingRuleFormDrawer.vue";
import RoutingRuleTestDrawer from "../../components/routing/RoutingRuleTestDrawer.vue";
import {listRoutingRuleOperationLogs} from "@/api/operationLogs";
import {listRoutingConsumers, listRoutingRules, getRoutingConsumer} from "@/api/routing";
import {normalizeOptionList} from "@/utils/options";

const {
  query,
  rows,
  loading,
  total,
  pageNum,
  pageSize,
  load,
  onPageChange,
  onSizeChange,
  resetPage,
  handleQuery,
  handleReset
} = useListQuery({ apiFn: listRoutingRules, fields: { keyword: "" } });

const formVisible = ref(false);
const editingRule = ref<Dict | null>(null);
const testVisible = ref(false);
const testRuleId = ref<number | null>(null);
const testRuleName = ref("");
const testRuleCode = ref("");
const testSecretKey = ref("");
const testTargets = ref<Row[]>([]);
const logDrawer = ref(false);
const logRuleId = ref<number | string | null>(null);
const logRuleName = ref("");

function openCreate() {
  editingRule.value = null;
  formVisible.value = true;
}

function openEdit(row: Row) {
  editingRule.value = row;
  formVisible.value = true;
}

function onRuleSaved() {
  resetPage();
}

async function openTest(row: Row) {
  testRuleId.value = row.id;
  testRuleName.value = row.name || row.ruleCode || "";
  testRuleCode.value = row.ruleCode || "";
  testSecretKey.value = await loadConsumerSecretKey(row.consumerIds);
  testTargets.value = Array.isArray(row.targets) ? row.targets : [];
  testVisible.value = true;
}

async function loadConsumerSecretKey(consumerIds: Array<string | number>) {
  const ids = Array.isArray(consumerIds) ? consumerIds : [];
  if (!ids.length) {
    return "";
  }
  // 按 id 精确取首个消费者的密钥：走详情端点而非全量列表
  // （下拉候选用 POST /options，刻意不含 secretKey；密钥只在详情按需取）
  try {
    const detail = await getRoutingConsumer(ids[0]);
    return detail?.secretKey || "";
  } catch {
    return "";
  }
}

function openChangeLog(row: Row) {
  logRuleId.value = row.id;
  logRuleName.value = row.name || row.ruleCode || `#${row.id}`;
  logDrawer.value = true;
}

function loadRoutingRuleOperationLogs() {
  return listRoutingRuleOperationLogs(logRuleId.value!);
}


load();
</script>
