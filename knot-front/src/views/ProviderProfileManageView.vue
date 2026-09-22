<template>
  <PageSection>
    <div class="list-page-shell">
      <section class="list-page-block">
        <div class="list-page-filters">
          <div class="list-filter-item list-filter-item--grow">
            <span class="list-filter-label">关键词</span>
            <el-input
              v-model="query.keyword"
              class="list-filter-control--wide"
              placeholder="按编码、名称筛选"
              clearable
              @keyup.enter="handleQuery"
            />
          </div>
          <div class="list-filter-item">
            <span class="list-filter-label">分类</span>
            <el-select v-model="query.category" class="list-filter-control" clearable placeholder="全部">
              <el-option v-for="item in tagPreset" :key="item" :label="item" :value="item" />
            </el-select>
          </div>
          <div class="list-filter-actions">
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>
      </section>

      <section class="list-page-block list-page-block--content">
        <div class="list-page-toolbar">
          <div class="list-page-toolbar__actions list-page-toolbar__actions--start">
            <el-button type="primary" @click="openCreate">新建供应商信息</el-button>
          </div>
        </div>

        <div class="list-page-table">
          <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" align="center" header-align="center" />
            <el-table-column prop="code" label="编码" min-width="18%" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" min-width="22%" show-overflow-tooltip />
            <el-table-column label="分类" min-width="22%">
              <template #default="{ row }">
                <template v-if="splitTags(row.tag).length">
                  <el-tag
                    v-for="item in splitTags(row.tag)"
                    :key="item"
                    size="small"
                    class="tag-item"
                  >
                    {{ item }}
                  </el-tag>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="18%">
              <template #default="{ row }">
                {{ formatTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="18%">
              <template #default="{ row }">
                {{ formatTime(row.updatedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center" header-align="center" fixed="right">
              <template #default="{ row }">
                <RowActions
                  :actions="[
                    { key: 'edit', label: '编辑', icon: Edit },
                    { key: 'log', label: '操作记录', icon: Document },
                    { key: 'delete', label: '删除', icon: Delete, type: 'danger', confirm: `确认删除「${row.name}」？` }
                  ]"
                  @action="(key) => onRowAction(key, row)"
                />
              </template>
            </el-table-column>
          </el-table>

          <ListPagination
            :total="total"
            :page-num="pageNum"
            :page-size="pageSize"
            :show-refresh="false"
            @page-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </section>
    </div>

    <el-drawer
      :model-value="formVisible"
      :title="editing ? '编辑供应商信息' : '新建供应商信息'"
      size="50%"
      class="drawer-with-scrollbar"
      destroy-on-close
      @update:model-value="formVisible = $event"
      @closed="onDrawerClosed"
    >
      <el-scrollbar max-height="calc(100vh - 140px)">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="drawer-form">
          <el-form-item label="编码" prop="code">
            <el-input v-model="form.code" maxlength="32" placeholder="例如 openai" />
          </el-form-item>
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" maxlength="100" placeholder="例如 OpenAI" />
          </el-form-item>
          <el-form-item label="分类" prop="tags">
            <el-select
              v-model="form.tags"
              multiple
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="可多选，也可直接输入自定义分类"
              style="width: 100%"
            >
              <el-option v-for="item in tagPreset" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-scrollbar>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-drawer>

    <OperationLogDrawer
      v-model="logDrawer"
      :title="`供应商信息变更记录 - ${logName || ''}`"
      :load-logs="loadProfileLogs"
    />
  </PageSection>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { Delete, Document, Edit } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import PageSection from "../components/common/PageSection.vue";
import ListPagination from "../components/common/ListPagination.vue";
import RowActions from "../components/common/RowActions.vue";
import OperationLogDrawer from "../components/common/OperationLogDrawer.vue";
import {
  checkProviderProfileCode,
  createProviderProfile,
  deleteProviderProfile,
  listProviderProfiles,
  updateProviderProfile
} from "../api/providerProfiles";
import { listProviderProfileOperationLogs } from "../api/operationLogs";
import { useAutoQuery } from "../composables/useAutoQuery";
import { usePageList } from "../composables/usePageList";

const tagPreset = ["原厂", "云厂商", "代理"];

const query = reactive({
  keyword: "",
  category: ""
});

const { rows, loading, total, pageNum, pageSize, load, onPageChange, onSizeChange, resetPage } =
  usePageList(listProviderProfiles, { extra: query });
const { pauseAutoQuery } = useAutoQuery(query, handleQuery);

const formVisible = ref(false);
const submitting = ref(false);
const formRef = ref(null);
const editing = ref(null);
const form = reactive({
  code: "",
  name: "",
  tags: []
});

const logDrawer = ref(false);
const logId = ref(null);
const logName = ref("");

const rules = {
  code: [
    { required: true, message: "请输入编码", trigger: "blur" },
    {
      validator: async (rule, value, callback) => {
        if (!value) {
          callback();
          return;
        }
        try {
          const result = await checkProviderProfileCode(value, editing.value?.id);
          callback(result?.available ? undefined : new Error("编码已被占用"));
        } catch {
          callback();
        }
      },
      trigger: "blur"
    }
  ],
  name: [{ required: true, message: "请输入名称", trigger: "blur" }],
  tags: [
    {
      validator: (rule, value, callback) => {
        const list = (value || []).map((item) => String(item).trim()).filter(Boolean);
        callback(list.length ? undefined : new Error("请至少选择一个分类"));
      },
      trigger: "change"
    }
  ]
};

function splitTags(raw) {
  if (!raw) return [];
  return String(raw)
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function openCreate() {
  editing.value = null;
  form.code = "";
  form.name = "";
  form.tags = [];
  formVisible.value = true;
}

function openEdit(row) {
  editing.value = row;
  form.code = row.code || "";
  form.name = row.name || "";
  form.tags = splitTags(row.tag);
  formVisible.value = true;
}

function onDrawerClosed() {
  editing.value = null;
  formRef.value?.clearValidate();
}

async function submit() {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      code: form.code.trim(),
      name: form.name.trim(),
      tag: form.tags.map((item) => String(item).trim()).filter(Boolean).join(",")
    };
    if (editing.value?.id) {
      await updateProviderProfile(editing.value.id, payload);
    } else {
      await createProviderProfile(payload);
    }
    ElMessage.success("保存成功");
    formVisible.value = false;
    await load();
  } finally {
    submitting.value = false;
  }
}

async function remove(row) {
  await deleteProviderProfile(row.id);
  ElMessage.success("删除成功");
  await load();
}

function openLog(row) {
  logId.value = row.id;
  logName.value = row.name || `#${row.id}`;
  logDrawer.value = true;
}

function loadProfileLogs() {
  return listProviderProfileOperationLogs(logId.value);
}

function onRowAction(key, row) {
  if (key === "edit") {
    openEdit(row);
  } else if (key === "log") {
    openLog(row);
  } else if (key === "delete") {
    remove(row);
  }
}

function handleQuery() {
  return pauseAutoQuery(() => resetPage());
}

function handleReset() {
  return pauseAutoQuery(() => {
    query.keyword = "";
    query.category = "";
    return resetPage();
  });
}

function formatTime(value) {
  if (!value) {
    return "-";
  }
  return String(value).replace("T", " ");
}

onMounted(load);
</script>

<style scoped>
.drawer-form {
  padding-right: 8px;
}

.tag-item {
  margin-right: 4px;
}
</style>
