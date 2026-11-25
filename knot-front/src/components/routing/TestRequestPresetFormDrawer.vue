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
              <p>维护预设编码、名称与关联协议，按协议与模型族归类、全局共享复用；模型族决定调试面板在哪些目标下提供该用例，留空为通用用例。</p>
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
              <el-form-item label="模型族">
                <EnumSelect
                  v-model="form.modelFamily"
                  category="model_family"
                  :disabled="readonly"
                  clearable
                  filterable
                  placeholder="可选：不选为通用用例"
                  style="width: 100%"
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
          <el-form-item label-width="0">
            <div class="preset-body-field">
              <!-- 错误提示放在输入框上方：编辑器固定 420px 高，挂在下方的 el-form-item__error 会落到
                   视线之外，保存时看不出是哪一栏没过校验。 -->
              <p v-if="bodyError" class="preset-body-field__error">{{ bodyError }}</p>
              <!-- height 必须给固定值：只给 max-height 时 el-scrollbar 的 wrap 高度算不出来
                   （其 height:100% 在无确定高度的父级上退化为 auto），超长请求体会被直接裁掉。 -->
              <JsonCodeEditor
                v-model="form.requestBody"
                :readonly="readonly"
                min-height="220px"
                max-height="420px"
                height="420px"
              />
            </div>
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
import EnumSelect from "../common/EnumSelect.vue";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";
import {createTestRequestPreset, getTestRequestPreset, updateTestRequestPreset} from "@/api/routing";
import type {Dict, Row} from "@/types";
import {formatJsonText, parseJsonResult} from "@/utils/format";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 编辑 / 查看态只接主键：打开时按 id 拉详情，不复用列表行数据 */
  presetId: {type: Number as PropType<number | null>, default: null},
  readonly: {type: Boolean, default: false}
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
  return props.presetId != null ? "编辑预设请求" : "新建预设请求";
});

const saving = ref(false);
const codeError = ref("");
const bodyError = ref("");

const form = reactive({
  id: null as number | null,
  code: "",
  name: "",
  protocolCode: "",
  modelFamily: "",
  requestBody: "",
  remark: "",
  enabled: true
});

watch(
  () => [props.modelValue, props.presetId] as const,
  async ([value]) => {
    if (!value) return;
    if (props.presetId == null) {
      resetForm(null);
      return;
    }
    let detail: Dict;
    try {
      const loaded = await getTestRequestPreset(props.presetId);
      if (!loaded) throw new Error("预设请求详情为空");
      detail = loaded as Dict;
    } catch {
      ElMessage.error("预设请求详情加载失败");
      emit("update:modelValue", false);
      return;
    }
    resetForm(detail);
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
  form.modelFamily = row?.modelFamily || "";
  form.requestBody = formatJsonText(row?.requestBody);
  form.remark = row?.remark ?? "";
  // 数字状态：0 是合法值（停用），不能用 truthy 判断
  form.enabled = row?.status === 0 ? false : true;
  codeError.value = "";
  bodyError.value = "";
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
    modelFamily: form.modelFamily || null,
    requestBody: form.requestBody,
    remark: form.remark?.trim() || null,
    status: form.enabled ? 1 : 0
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
    if (props.presetId != null) {
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

.preset-body-field {
  width: 100%;
}

.preset-body-field__error {
  margin: 0 0 8px;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.4;
}

</style>
