<template>
  <PageSection title="Usage 提取器说明">
    <div class="extractor-guide">
      <p class="extractor-guide__intro">
        提取器决定网关从上游响应里「取什么量」：按协议把响应体（连同本次请求体）转成对应计费模式的用量记录，
        再交给计费规则计价。在供应商模型的 API 绑定中选择提取器时，可对照本页说明按模型协议与计费模式选用。
      </p>

      <el-table v-loading="loading" :data="rows" stripe border style="width: 100%">
        <el-table-column prop="code" label="编码" width="120" align="center" header-align="center" fixed="left">
          <template #default="{ row }">
            <span class="extractor-guide__code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="label" label="名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ displayText(row.label) }}</span>
            <el-tag v-if="row.code === rows[0]?.code" size="small" type="info" effect="plain" class="extractor-guide__default-tag">
              默认
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="380">
          <template #default="{ row }">
            <div class="extractor-guide__desc">{{ displayText(row.description) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="计费模式" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="mode in billingModesOf(row)"
              :key="mode"
              size="small"
              class="extractor-guide__mode-tag"
              effect="plain"
            >
              {{ modeLabel(mode) }}
            </el-tag>
            <span v-if="!billingModesOf(row).length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="流式" width="90" align="center">
          <template #default="{ row }">
            <span>{{ row.streamSupported === false ? "不支持" : "支持" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="className" label="实现类" min-width="260" show-overflow-tooltip />
      </el-table>
    </div>
  </PageSection>
</template>

<script setup lang="ts">
import {displayText} from "@/utils/format";
import type {Row} from "@/types";
import {onMounted, ref} from "vue";
import {ElMessage} from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {listUsageExtractors} from "@/api/models";

/**
 * Usage 提取器目录页：只读展示全部提取器及其说明（GET /api/models/usage-extractors）。
 *
 * <p>数据与供应商模型表单里的提取器下拉同源（UsageExtractorCatalog），本页是它的说明书——
 * 下拉按已绑计费模式收敛后看不到全貌，配置时可从表单的「说明」链接跳到这里对照。</p>
 */
const {labelOf: enumLabelOf} = useEnumOptions();

const rows = ref<Row[]>([]);
const loading = ref(false);

function billingModesOf(row: Row): string[] {
  const modes = row?.billingModes;
  return Array.isArray(modes) ? modes.map((code) => String(code)) : [];
}

function modeLabel(code: string): string {
  return enumLabelOf("BillingModeEnum", code, code);
}

async function load() {
  loading.value = true;
  try {
    const data = await listUsageExtractors();
    rows.value = Array.isArray(data) ? data : [];
  } catch (error) {
    ElMessage.error((error as Error)?.message || "加载提取器列表失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.extractor-guide__intro {
  margin: 0 0 16px 0;
  color: #606266;
  line-height: 1.7;
}

.extractor-guide__code {
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 13px;
}

.extractor-guide__default-tag {
  margin-left: 6px;
  vertical-align: middle;
}

.extractor-guide__desc {
  white-space: normal;
  line-height: 1.7;
  color: #606266;
  padding: 4px 0;
}

.extractor-guide__mode-tag {
  margin: 2px 4px 2px 0;
}
</style>
