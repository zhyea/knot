<template>
  <PageSection>
    <div class="list-page-shell">
      <FilterBar @query="handleQuery" @reset="handleReset">
        <KeywordInput
          v-model="query.keyword"
          placeholder="按规则编码、模型族筛选"
          @query="handleQuery"
        />
        <FilterField label="模型族" :width="220">
          <EnumSelect
            v-model="query.modelFamilyCode"
            category="model_family"
            clearable
            placeholder="全部模型族"
          />
        </FilterField>
      </FilterBar>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建规则</el-button>
          </div>
        </div>

        <BillingRuleListPanel
          :rows="rows"
          :loading="loading"
          :total="total"
          :page-num="pageNum"
          :page-size="pageSize"
          :toggling-id="togglingId"
          :show-refresh="false"
          @edit="openEdit"
          @copy="openCopy"
          @log="openChangeLog"
          @preview="openPreview"
          @delete="handleDelete"
          @enabled-change="handleEnabledChange"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </div>

    <BillingRuleFormDialog v-model="ruleDlg" :rule-id="currentRuleId" :copy-from-id="copyFromId" @saved="resetPage" />

    <PricingPreviewDrawer v-model="previewDrawer" :rule="previewRule" />

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`计费规则变更日志 - ${logRuleName || ''}`"
      :load-logs="loadBillingRuleOperationLogs"
    />
  </PageSection>
</template>

<script setup lang="ts">
import type {Row} from "@/types";
import {ref} from "vue";
import {ElMessage} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import FilterField from "../../components/common/FilterField.vue";
import KeywordInput from "../../components/common/KeywordInput.vue";
import EnumSelect from "../../components/common/EnumSelect.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import BillingRuleFormDialog from "../../components/billing/BillingRuleFormDialog.vue";
import BillingRuleListPanel from "../../components/billing/BillingRuleListPanel.vue";
import PricingPreviewDrawer from "../../components/billing/PricingPreviewDrawer.vue";
import {useEnabledToggle} from "@/composables/useEnabledToggle";
import {useListQuery} from "@/composables/useListQuery";
import {deleteBillingRule, listBillingRules, updateBillingRuleStatus} from "@/api/billing";
import {listBillingRuleOperationLogs} from "@/api/operationLogs";
import {useLogDrawer} from "@/composables/useLogDrawer";
import {useFormDrawer} from "@/composables/useFormDrawer";
const {id: logRuleId, name: logRuleName, visible: logDrawer, open: openChangeLog} = useLogDrawer({labelFields: ["code"]});
const {visible: ruleDlg, editing: currentRuleId, openCreate, openEdit} = useFormDrawer<number | null>({emptyValue: null, toEditing: (row) => Number(row.id), onOpen: () => { copyFromId.value = null; }});

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
} = useListQuery({ apiFn: listBillingRules, fields: { keyword: "", modelFamilyCode: null } });

const {togglingId, handleEnabledChange} = useEnabledToggle({
  updateApi: updateBillingRuleStatus,
  onChanged: () => load()
});

/** 编辑目标规则 id：编辑抽屉打开时按 id 从后端取全量记录（列表行是轻量 VO） */
/** 克隆源规则 id：非空时抽屉按 id 取全量记录并预填，保存为新建规则（编码留空） */
const copyFromId = ref<number | null>(null);
const previewDrawer = ref(false);
const previewRule = ref<Row | null>(null);

function openCopy(row: Row) {
  currentRuleId.value = null;
  copyFromId.value = Number(row.id);
  ruleDlg.value = true;
}

function openPreview(row: Row) {
  previewRule.value = row;
  previewDrawer.value = true;
}

function loadBillingRuleOperationLogs() {
  return listBillingRuleOperationLogs(logRuleId.value!);
}

async function handleDelete(row: Row) {
  await deleteBillingRule(row.id);
  ElMessage.success("已删除");
  await load();
}


load();
</script>
