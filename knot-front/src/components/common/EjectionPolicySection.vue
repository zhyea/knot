<template>
  <div class="ejection-policy">
    <div v-if="title || description" class="ejection-policy__head">
      <div>
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <el-form-item label="启用" class="ejection-policy__switch">
        <el-switch v-model="draft.enabled"/>
      </el-form-item>
    </div>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-form-item label="配置模式" label-width="110px">
          <el-radio-group v-model="draft.mode" :disabled="!draft.enabled">
            <el-radio-button :value="EJECTION_MODE_SIMPLE">简单</el-radio-button>
            <el-radio-button :value="EJECTION_MODE_PROFESSIONAL">深度</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="异常窗口(秒)" label-width="130px">
          <el-input-number
            v-model="draft.windowSec"
            :min="1"
            :max="EJECTION_WINDOW_SEC_LIMIT"
            :disabled="!draft.enabled"
            controls-position="right"
            class="ejection-policy__field"
          />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="异常次数阈值" label-width="120px">
          <el-input-number
            v-model="draft.threshold"
            :min="1"
            :max="EJECTION_THRESHOLD_LIMIT"
            :disabled="!draft.enabled"
            controls-position="right"
            class="ejection-policy__field"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-form-item label="首token慢阈值(ms)" label-width="130px">
          <el-input-number
            v-model="draft.slowFirstTokenMs"
            :min="1"
            :max="EJECTION_SLOW_FIRST_TOKEN_MS_LIMIT"
            :step="1000"
            :disabled="!draft.enabled"
            controls-position="right"
            class="ejection-policy__field"
          />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="冷却时长(ms)" label-width="120px">
          <el-input-number
            v-model="draft.cooldownMs"
            :min="EJECTION_COOLDOWN_MS_MIN"
            :max="EJECTION_COOLDOWN_MS_LIMIT"
            :step="5000"
            :disabled="!draft.enabled"
            controls-position="right"
            class="ejection-policy__field"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <template v-if="draft.mode === EJECTION_MODE_PROFESSIONAL">
      <el-row :gutter="16">
        <el-col :span="4">
          <el-form-item label-width="110px">
            <template #label>
              <span class="ejection-policy__label">
                统计失败
                <el-tooltip :content="tips.countFailures" placement="top">
                  <el-icon class="ejection-policy__label-icon"><QuestionFilled/></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-switch v-model="draft.countFailures" :disabled="!draft.enabled"/>
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label-width="110px">
            <template #label>
              <span class="ejection-policy__label">
                统计慢响应
                <el-tooltip :content="tips.countSlow" placement="top">
                  <el-icon class="ejection-policy__label-icon"><QuestionFilled/></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-switch v-model="draft.countSlow" :disabled="!draft.enabled"/>
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label-width="110px">
            <template #label>
              <span class="ejection-policy__label">
                全摘时放行
                <el-tooltip :content="tips.failOpenWhenAllEjected" placement="top">
                  <el-icon class="ejection-policy__label-icon"><QuestionFilled/></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-switch v-model="draft.failOpenWhenAllEjected" :disabled="!draft.enabled"/>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label-width="110px">
            <template #label>
              <span class="ejection-policy__label">
                冷却增长倍数
                <el-tooltip :content="tips.cooldownMultiplier" placement="top">
                  <el-icon class="ejection-policy__label-icon"><QuestionFilled/></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-input-number
              v-model="draft.cooldownMultiplier"
              :min="1"
              :max="EJECTION_COOLDOWN_MULTIPLIER_LIMIT"
              :step="0.1"
              :precision="1"
              :disabled="!draft.enabled"
              controls-position="right"
              class="ejection-policy__field"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label-width="110px">
            <template #label>
              <span class="ejection-policy__label">
                冷却上限(ms)
                <el-tooltip :content="tips.maxCooldownMs" placement="top">
                  <el-icon class="ejection-policy__label-icon"><QuestionFilled/></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-input-number
              v-model="draft.maxCooldownMs"
              :min="draft.cooldownMs"
              :max="EJECTION_COOLDOWN_MS_LIMIT"
              :step="60000"
              :disabled="!draft.enabled"
              controls-position="right"
              class="ejection-policy__field"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label-width="110px">
            <template #label>
              <span class="ejection-policy__label">
                探活租约(ms)
                <el-tooltip :content="tips.probeTimeoutMs" placement="top">
                  <el-icon class="ejection-policy__label-icon"><QuestionFilled/></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-input-number
              v-model="draft.probeTimeoutMs"
              :min="EJECTION_COOLDOWN_MS_MIN"
              :max="EJECTION_PROBE_TIMEOUT_MS_LIMIT"
              :step="5000"
              :disabled="!draft.enabled"
              controls-position="right"
              class="ejection-policy__field"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </template>

    <p class="ejection-policy__hint">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
import {QuestionFilled} from "@element-plus/icons-vue";
import {useDraftModel} from "@/composables/useDraftModel";
import {
  EJECTION_COOLDOWN_MS_LIMIT,
  EJECTION_COOLDOWN_MS_MIN,
  EJECTION_COOLDOWN_MULTIPLIER_LIMIT,
  EJECTION_MODE_PROFESSIONAL,
  EJECTION_MODE_SIMPLE,
  EJECTION_PROBE_TIMEOUT_MS_LIMIT,
  EJECTION_SLOW_FIRST_TOKEN_MS_LIMIT,
  EJECTION_THRESHOLD_LIMIT,
  EJECTION_WINDOW_SEC_LIMIT,
  normalizeEjectionPolicy,
  type EjectionPolicy
} from "@/utils/ejectionPolicy";

const props = withDefaults(defineProps<{
  title?: string;
  description?: string;
  ejectionPolicy?: EjectionPolicy;
}>(), {
  title: "",
  description: "",
  ejectionPolicy: undefined
});

const emit = defineEmits<{
  (e: "update:ejectionPolicy", value: EjectionPolicy): void;
}>();

const hint =
  "摘除作用在「切换候选」之前：窗口内反复异常（上游故障类失败或首 token 过慢）的供应商模型，"
  + "在后续请求里直接被移出候选链，省掉明知道是坏目标还要先试一遍的延迟。"
  + "只统计模型本身的问题——4xx 业务错误、配置类错误、网关自身限流都不计数；上游 429 与 5xx 才计。"
  + "冷却结束后放行一个探活请求，成功即恢复，失败则冷却翻倍（上限见深度模式）。"
  + "默认关闭：摘除会主动减少可用容量，误判代价高于漏判。";

/** 深度模式字段说明：仅在展开「深度」模式后随字段一起出现，避免简单模式被长文案淹没 */
const tips = {
  countFailures:
    "上游故障类失败（5xx、上游 429、连接/读超时）是否计入窗口次数，默认开启；关闭后只看慢信号。"
    + "4xx 业务错误、配置类错误、网关自身限流一律不计——摘除只盯「这个目标坏了」，调用方或网关自己的锅不算。",
  countSlow:
    "首 token 超过慢阈值是否计入窗口次数，默认开启；失败与慢信号共用同一个计数器，两者之和达阈值即摘除。"
    + "注意成功只清失败计数，慢计数靠窗口过期、探活成功或手动恢复清除。",
  failOpenWhenAllEjected:
    "候选被全部摘除时忽略摘除、按原候选链放行，默认开启。摘除是「少打坏目标」的优化，"
    + "不能演变成整体不可用；关闭后全摘除会因为没有可用候选而直接报错。",
  cooldownMultiplier:
    "探活请求失败后冷却时长的增长倍数：下一次冷却 = 当前冷却 × 本倍数，取值 1.0–10.0（保留 1 位小数），默认 2.0。",
  maxCooldownMs:
    "冷却翻倍增长的封顶值，默认 600000（10 分钟）。不会低于「冷却时长」——首次摘除的冷却不会被上限压小。",
  probeTimeoutMs:
    "冷却结束后放行的探活请求的租约时长，默认 30000（30 秒）。租约期内同一时刻只放行一个探活请求，"
    + "避免刚恢复的目标被一窝蜂的探活流量打回原形。"
};

/** 本地草稿 + 双向同步：不直接改 props 对象，回环由 useDraftModel 内的结构比较阻断 */
const draft = useDraftModel<EjectionPolicy>(
  () => props.ejectionPolicy,
  normalizeEjectionPolicy,
  value => emit("update:ejectionPolicy", value)
);
</script>

<style scoped>
.ejection-policy {
  display: block;
}

.ejection-policy__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.ejection-policy__head h3 {
  margin: 0 0 4px;
  color: var(--knot-text, #303133);
  font-size: 15px;
  font-weight: 600;
}

.ejection-policy__head p {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.ejection-policy__switch {
  flex: 0 0 auto;
  margin-bottom: 0;
}

.ejection-policy__field {
  width: 100%;
}

/* label 尽量不换行 */
.ejection-policy :deep(.el-form-item__label) {
  white-space: nowrap;
}

/* 深度模式字段的 hover 说明图标：与 label 基线对齐，不撑高表单行 */
.ejection-policy__label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ejection-policy__label-icon {
  color: #c0c4cc;
  font-size: 13px;
  cursor: help;
  outline: none;
}

.ejection-policy__label-icon:hover {
  color: var(--el-color-primary, #409eff);
}

.ejection-policy__hint {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
