<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    size="720px"
    class="drawer-with-scrollbar"
    destroy-on-close
    @closed="onClosed"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <el-form :model="form" label-width="96px" class="preset-form" :disabled="readonly">
        <div class="slot-body preset-section">
          <div class="section-head">
            <div>
              <h3>基础信息</h3>
              <p>维护预设编码、名称与关联协议，按协议归类、全局共享复用；统一模型为可选归类维度，不影响调试面板的用例过滤。</p>
            </div>
            <el-form-item label="启用" class="inline-switch" :disabled="readonly">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </div>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="预设编码" required :error="codeError">
                <el-input
                  v-model="form.code"
                  maxlength="64"
                  show-word-limit
                  :disabled="readonly"
                  placeholder="人工填写，唯一"
                  @blur="validateCode"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="名称" required>
                <el-input v-model="form.name" :disabled="readonly" maxlength="100" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="协议" required>
                <EnumControl
                  v-model="form.protocolCode"
                  enum-name="ModelApiProtocolEnum"
                  filterable
                  :disabled="readonly"
                  placeholder="选择关联协议"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="统一模型">
                <RemoteEntitySelect
                  v-model="form.logicalModelCode"
                  value-key="modelCode"
                  :load-function="loadLogicalModelOptions"
                  :label-function="logicalModelLabel"
                  :selected-options="selectedLogicalModelOptions"
                  :code-only="codeOnly"
                  :disabled="readonly"
                  clearable
                  placeholder="可选，仅作归类"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="备注">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="2"
              :disabled="readonly"
              maxlength="255"
              show-word-limit
              placeholder="可选"
            />
          </el-form-item>
        </div>

        <div class="space-line" />

        <div class="slot-body preset-section">
          <div class="section-head">
            <div>
              <h3>请求体</h3>
              <p>维护完整、具体的请求体 JSON；调试面板按当前目标覆盖 model 字段。</p>
            </div>
          </div>
          <el-form-item label-width="0" required :error="bodyError">
            <JsonCodeEditor
              v-model="form.requestBody"
              :readonly="readonly"
              min-height="220px"
              max-height="420px"
            />
          </el-form-item>
        </div>
      </el-form>
    </el-scrollbar>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="!readonly" type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {type PropType, computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import EnumControl from "../common/EnumControl.vue";
import RemoteEntitySelect from "../common/RemoteEntitySelect.vue";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";
import {createTestRequestPreset, updateTestRequestPreset} from "@/api/routing";
import {listLogicalModels} from "@/api/logicalModels";
import type {Dict, Row} from "@/types";
import {mergeOptionList, normalizeOptionList, resolveSelectedOption} from "@/utils/options";
import {formatJsonText, parseJsonResult} from "@/utils/format";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  preset: {type: Object as PropType<Dict | null>, default: null},
  readonly: {type: Boolean, default: false},
  /** 统一模型下拉仅展示 modelCode */
  codeOnly: {type: Boolean, default: false}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const drawerTitle = computed(() => {
  if (props.readonly) {
    return "查看预设请求";
  }
  return props.preset ? "编辑预设请求" : "新建预设请求";
});

const saving = ref(false);
const codeError = ref("");
const bodyError = ref("");
const logicalModelOptions = ref<Row[]>([]);

const form = reactive({
  id: null as number | null,
  code: "",
  name: "",
  protocolCode: "",
  logicalModelCode: null as string | null,
  logicalModelName: "" as string | undefined,
  requestBody: "",
  remark: "",
  enabled: true
});

/** 统一模型为可清空的可选维度：已选项需回显名称，未归类时为空数组 */
const selectedLogicalModelOptions = computed(() =>
  resolveSelectedOption(form.logicalModelCode, logicalModelOptions.value, {
    modelCode: form.logicalModelCode,
    modelName: form.logicalModelName
  }, "modelCode")
);

function logicalModelLabel(model: Row) {
  return model.modelCode ? `${model.modelName || model.modelCode}（${model.modelCode}）` : `#${model.id}`;
}

async function loadLogicalModelOptions(params: Dict) {
  const res = await listLogicalModels(params);
  logicalModelOptions.value = mergeOptionList(logicalModelOptions.value, normalizeOptionList(res), "modelCode");
  return res;
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      resetForm(props.preset);
    }
  }
);

watch(
  () => form.code,
  () => {
    if (codeError.value) {
      codeError.value = "";
    }
  }
);

function resetForm(row: Row | null = null) {
  form.id = row?.id ?? null;
  form.code = row?.code || "";
  form.name = row?.name || "";
  form.protocolCode = row?.protocolCode || "";
  form.logicalModelCode = row?.logicalModelCode || null;
  form.logicalModelName = row?.logicalModelName;
  form.requestBody = formatJsonText(row?.requestBody);
  form.remark = row?.remark ?? "";
  form.enabled = row?.status !== "INACTIVE";
  codeError.value = "";
  bodyError.value = "";
  logicalModelOptions.value = [];
}

function onClosed() {
  form.id = null;
}

function validateCode(): boolean {
  const code = form.code?.trim();
  if (!code) {
    codeError.value = "请填写预设编码";
    return false;
  }
  codeError.value = "";
  return true;
}

function validateBody(): boolean {
  const text = form.requestBody?.trim();
  if (!text) {
    bodyError.value = "请填写请求体";
    return false;
  }
  if (parseJsonResult(text, null).error) {
    bodyError.value = "请求体不是合法 JSON";
    return false;
  }
  bodyError.value = "";
  return true;
}

function buildPayload() {
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    protocolCode: form.protocolCode,
    // 可选归类维度：留空提交 null（通用用例）
    logicalModelCode: form.logicalModelCode || null,
    requestBody: form.requestBody,
    remark: form.remark?.trim() || null,
    status: form.enabled ? "ACTIVE" : "INACTIVE"
  };
}

async function submit() {
  if (!form.name?.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  if (!form.protocolCode) {
    ElMessage.warning("请选择协议");
    return;
  }
  if (!validateCode() || !validateBody()) {
    return;
  }
  saving.value = true;
  try {
    if (props.preset) {
      await updateTestRequestPreset(form.id!, buildPayload());
      ElMessage.success("已保存");
    } else {
      await createTestRequestPreset(buildPayload());
      ElMessage.success("已创建");
    }
    visible.value = false;
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.preset-form {
  padding-bottom: 8px;
}

.preset-section {
  border-color: #e4e7ed;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.section-head h3 {
  margin: 0 0 4px;
  color: var(--knot-text, #303133);
  font-size: 15px;
  font-weight: 600;
}

.section-head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.inline-switch {
  flex: 0 0 auto;
  margin-bottom: 0;
}

.space-line {
  height: 14px;
}
</style>
