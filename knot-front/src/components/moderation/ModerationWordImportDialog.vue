<template>
  <el-dialog
    :model-value="modelValue"
    title="批量导入敏感词"
    width="720px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert type="info" :closable="false" show-icon class="import-tip">
      <template #title>
        每行一条，格式：<code>敏感词,分类编码,严重度,语言,备注</code>。分类编码如 POLITICS / VIOLENCE /
        PORN / AD / ABUSE / CUSTOM；严重度 1-3（缺省 2）；语言缺省 zh。逗号分隔，备注里不要写逗号。
      </template>
    </el-alert>

    <el-input
      v-model="text"
      type="textarea"
      :rows="12"
      placeholder="示例：&#10;测试敏感词,ABUSE,2,zh,演示用&#10;BADWORD,AD,3,en"
      @input="parse"
    />

    <div v-if="parsed.length > 0" class="import-preview">
      已解析 <b>{{ parsed.length }}</b> 条待导入
    </div>
    <div v-if="parseError" class="import-error">{{ parseError }}</div>

    <el-dialog
      :model-value="resultVisible"
      title="导入结果"
      width="560px"
      append-to-body
      destroy-on-close
      @update:model-value="resultVisible = $event"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="总行数">{{ result?.total ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="新导入">{{ result?.inserted ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="已存在跳过">{{ result?.skipped ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="失败行">{{ result?.errors?.length ?? 0 }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="result?.errors?.length" class="import-result-errors">
        <div class="import-result-errors__title">失败明细（前 50 条）：</div>
        <ul>
          <li v-for="(line, index) in displayErrors" :key="index">{{ line }}</li>
        </ul>
      </div>
      <div v-else class="import-result-ok">全部导入成功。</div>

      <template #footer>
        <el-button type="primary" @click="closeResult">知道了</el-button>
      </template>
    </el-dialog>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="parsed.length === 0" @click="submit">
        导入 {{ parsed.length > 0 ? `(${parsed.length})` : "" }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {computed, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import type {Dict, Row} from "@/types";
import {importModerationWords} from "@/api/moderationWords";

const props = defineProps({
  modelValue: {type: Boolean, default: false}
});

const emit = defineEmits(["update:modelValue", "imported"]);

const saving = ref(false);
const text = ref("");
const parseError = ref("");
const parsed = ref<Dict[]>([]);
const resultVisible = ref(false);
const result = ref<Row | null>(null);

const displayErrors = computed(() => (result.value?.errors as string[] | undefined)?.slice(0, 50) ?? []);

watch(() => props.modelValue, (visible) => {
  if (visible) {
    text.value = "";
    parseError.value = "";
    parsed.value = [];
    result.value = null;
    resultVisible.value = false;
  }
});

/**
 * 解析粘贴文本：每行一条，逗号分隔。空行跳过；列数不足或分类为空即报错并阻断导入，
 * 避免把明显损坏的文件整批提交上去。
 */
function parse() {
  parseError.value = "";
  const lines = text.value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const rows: Dict[] = [];
  for (let i = 0; i < lines.length; i++) {
    const lineNo = i + 1;
    const parts = lines[i].split(",").map((part) => part.trim());
    if (parts.length < 2 || !parts[0] || !parts[1]) {
      parseError.value = `第 ${lineNo} 行格式不正确：至少需要「敏感词,分类编码」`;
      parsed.value = [];
      return;
    }
    const severity = parts[2] ? Number(parts[2]) : 2;
    if (!Number.isInteger(severity) || severity < 1 || severity > 3) {
      parseError.value = `第 ${lineNo} 行严重度必须为 1-3（当前：${parts[2] || "空"}）`;
      parsed.value = [];
      return;
    }
    rows.push({
      word: parts[0],
      categoryCode: parts[1],
      severity,
      lang: parts[3] || "zh",
      remark: parts.slice(4).join(",") || null
    });
  }
  parsed.value = rows;
}

async function submit() {
  if (parsed.value.length === 0) {
    return;
  }
  saving.value = true;
  try {
    const res = await importModerationWords(parsed.value) as Row;
    result.value = res;
    resultVisible.value = true;
    if ((res?.inserted ?? 0) > 0) {
      emit("imported");
    }
  } finally {
    saving.value = false;
  }
}

function closeResult() {
  resultVisible.value = false;
  emit("update:modelValue", false);
  ElMessage.success("导入完成");
}
</script>

<style scoped>
.import-tip {
  margin-bottom: 12px;
}

.import-tip code {
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--el-fill-color-light);
  font-family: Consolas, Monaco, monospace;
}

.import-preview {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.import-error {
  margin-top: 8px;
  color: var(--el-color-danger);
  font-size: 13px;
}

.import-result-errors {
  margin-top: 12px;
  max-height: 220px;
  overflow-y: auto;
  font-size: 13px;
}

.import-result-errors__title {
  margin-bottom: 6px;
  font-weight: 600;
}

.import-result-errors ul {
  margin: 0;
  padding-left: 18px;
  color: var(--el-color-danger);
}

.import-result-ok {
  margin-top: 12px;
  color: var(--el-color-success);
  font-size: 13px;
}
</style>
