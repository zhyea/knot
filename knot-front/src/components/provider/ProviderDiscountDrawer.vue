<template>
  <el-drawer
    :model-value="modelValue"
    :title="`折扣策略 - 供应商 #${providerId ?? ''}`"
    size="50%"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <div class="drawer-toolbar">
        <el-button type="primary" size="small" @click="openCreate">新增策略</el-button>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" size="small" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
        <el-table-column prop="policyName" label="策略名" min-width="25%" />
        <el-table-column prop="discountType" label="类型" min-width="12%" />
        <el-table-column prop="discountValue" label="值" min-width="10%" />
        <el-table-column prop="status" label="状态" min-width="10%" />
        <el-table-column label="操作" width="80" align="center" header-align="center" fixed="right">
          <template #default="{ row }">
            <RowActions
              :actions="[{ key: 'edit', label: '编辑', icon: Edit }]"
              @action="openEdit(row)"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-scrollbar>

    <ProviderDiscountFormDialog
      v-model="formVisible"
      :provider-id="providerId"
      :policy="editingPolicy"
      @saved="onFormSaved"
    />
  </el-drawer>
</template>

<script setup>
import { ref, watch } from "vue";
import { Edit } from "@element-plus/icons-vue";
import RowActions from "../common/RowActions.vue";
import { listDiscountPolicies } from "../../api/providers";
import ProviderDiscountFormDialog from "./ProviderDiscountFormDialog.vue";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  providerId: { type: Number, default: null }
});

const emit = defineEmits(["update:modelValue", "changed"]);

const loading = ref(false);
const rows = ref([]);
const formVisible = ref(false);
const editingPolicy = ref(null);

async function load() {
  if (!props.providerId) return;
  loading.value = true;
  try {
    const res = await listDiscountPolicies(props.providerId);
    rows.value = Array.isArray(res) ? res : res?.list || [];
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingPolicy.value = null;
  formVisible.value = true;
}

function openEdit(row) {
  editingPolicy.value = row;
  formVisible.value = true;
}

async function onFormSaved() {
  await load();
  emit("changed");
}

function onClosed() {
  rows.value = [];
  formVisible.value = false;
  editingPolicy.value = null;
}

watch(
  () => [props.modelValue, props.providerId],
  ([visible, providerId]) => {
    if (visible && providerId) load();
  }
);

defineExpose({ reload: load });
</script>
