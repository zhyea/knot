<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="760px"
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div v-loading="loading" class="profile-form">
      <!--
        模板只在新建时给：编辑态换模板等于换掉一整组表达式，
        而那条档案可能已被模型引用，误点一次就会让在途任务按新口径判。
      -->
      <div v-if="isCreate" class="profile-form__template">
        <el-select v-model="templateKey" placeholder="从厂商模板预填（可选）" clearable style="width: 320px">
          <el-option v-for="item in templates" :key="item.key" :label="item.label" :value="item.key" />
        </el-select>
        <span class="profile-form__hint">{{ templateHint }}</span>
      </div>

      <el-form label-width="150px" label-position="right" size="default">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="档案编码" required>
          <el-input v-model="form.profileCode" :disabled="!isCreate" placeholder="大写字母开头，如 KLING_TASK" />
          <div class="profile-form__hint">
            绑定表按它引用；<b>已绑定时不能改</b>——改了等于让所有引用指向不存在的档案
          </div>
        </el-form-item>
        <el-form-item label="接口协议" required>
          <el-select v-model="form.protocol" :disabled="!isCreate" placeholder="选择协议" style="width: 100%">
            <el-option
              v-for="item in protocolOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
              :disabled="streamProtocols.has(String(item.value))"
            />
          </el-select>
          <div class="profile-form__hint">流式协议不可异步（响应会先结束），已置灰</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="enabled" />
          <span class="profile-form__hint profile-form__hint--inline">停用后新请求回落同步，在途任务仍按快照跑完</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="给下一个人看的说明，如「可灵 3.0 Turbo」" />
        </el-form-item>

        <el-divider content-position="left">查询口径</el-divider>
        <el-form-item label="查询方法" required>
          <el-radio-group v-model="form.queryMethod">
            <el-radio-button value="GET">GET</el-radio-button>
            <el-radio-button value="POST">POST</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="查询路径" required>
          <el-input v-model="form.queryPath" placeholder="/v1/videos/text2video/{task_id}" />
          <div class="profile-form__hint">
            必须含 <code>{task_id}</code> 占位（可出现在 path 段或 query 段）
          </div>
        </el-form-item>

        <el-divider content-position="left">取值表达式（MVEL）</el-divider>
        <el-form-item label="任务 ID" required>
          <el-input v-model="form.taskIdPath" placeholder="data.task_id" />
          <div class="profile-form__hint">从<b>创建响应</b>取上游任务 ID；取不到会回落同步</div>
        </el-form-item>
        <el-form-item label="状态" required>
          <el-input v-model="form.statusPath" placeholder="data.task_status" />
          <div class="profile-form__hint">从<b>查询响应</b>取状态文本</div>
        </el-form-item>
        <el-form-item label="成功判定" required>
          <el-input v-model="form.statusSucceeded" placeholder="['succeed','success'] contains status" />
        </el-form-item>
        <el-form-item label="失败判定" required>
          <el-input v-model="form.statusFailed" placeholder="status == 'failed'" />
        </el-form-item>
        <el-form-item label="进行中判定">
          <el-input v-model="form.statusRunning" placeholder="留空 = 除成功/失败外一律非终态" />
          <div class="profile-form__hint">留空最省事：上游新增中间态时不用改档案</div>
        </el-form-item>

        <el-divider content-position="left">产物与失败原因</el-divider>
        <el-form-item label="产物地址" required>
          <el-input v-model="form.resultPath" placeholder="data.task_result.videos" />
          <div class="profile-form__hint">
            标量 = 单个产物；数组 = 多个产物（视频一次生成 n 个时用它）
          </div>
        </el-form-item>
        <el-form-item label="产物字段名">
          <el-input v-model="form.resultUrlField" placeholder="url" />
          <div class="profile-form__hint">
            产物是<b>对象数组</b>时必填（如 videos[].url 填 url）；元素本身是 URL 字符串则留空
          </div>
        </el-form-item>
        <el-form-item label="失败原因">
          <el-input v-model="form.errorMessagePath" placeholder="data.task_status_msg" />
          <div class="profile-form__hint">留空时取上游顶层 message</div>
        </el-form-item>

        <el-divider content-position="left">轮询参数</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="首次间隔(ms)">
              <el-input-number v-model="form.pollIntervalMs" :min="100" :max="3600000" :step="500" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="退避基数(ms)">
              <el-input-number v-model="form.pollBackoffBaseMs" :min="100" :max="3600000" :step="500" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="退避上限(ms)">
              <el-input-number v-model="form.pollBackoffMaxMs" :min="100" :max="3600000" :step="1000" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="轮询次数上限">
              <el-input-number v-model="form.maxAttempts" :min="1" :max="10000" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务超时(ms)">
              <el-input-number v-model="form.timeoutMs" :min="1000" :max="86400000" :step="60000" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产物有效期(天)">
              <el-input-number v-model="form.resultExpiresDays" :min="1" :max="3650" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="profile-form__hint">
          次数上限与超时<b>先到先生效</b>；实际节拍取决于网关扫描 cron（分钟级），上面的间隔是下限
        </div>
      </el-form>

      <el-divider content-position="left">试跑（保存前可验）</el-divider>
      <div class="profile-form__dry-run">
        <div class="profile-form__dry-run-editor">
          <div class="profile-form__dry-run-label">创建响应（可选）</div>
          <JsonCodeEditor v-model="dryRunCreate" height="160px" />
        </div>
        <div class="profile-form__dry-run-editor">
          <div class="profile-form__dry-run-label">查询响应（可选）</div>
          <JsonCodeEditor v-model="dryRunQuery" height="160px" />
        </div>
        <div class="profile-form__dry-run-actions">
          <el-button type="primary" plain :loading="dryRunning" @click="onDryRun">按当前表达式试跑</el-button>
          <span class="profile-form__hint">
            贴一段真实的上游返回即可；表达式写错或状态谁都匹配不上，这里都会指出来
          </span>
        </div>

        <template v-if="dryResult">
          <el-alert
            v-for="(item, index) in dryResult.errors ?? []"
            :key="`e-${index}`"
            class="profile-form__alert"
            type="error"
            :closable="false"
            show-icon
            :title="item"
          />
          <el-alert
            v-for="(item, index) in dryResult.warnings ?? []"
            :key="`w-${index}`"
            class="profile-form__alert"
            type="warning"
            :closable="false"
            show-icon
            :title="item"
          />
          <el-descriptions :column="2" border size="small" class="profile-form__result">
            <el-descriptions-item label="任务 ID">{{ displayText(dryResult.upstreamTaskId) }}</el-descriptions-item>
            <el-descriptions-item label="状态原文">{{ displayText(dryResult.status) }}</el-descriptions-item>
            <el-descriptions-item label="判定">
              <el-tag :type="terminalTagType(dryResult.terminal)" size="small">
                {{ terminalLabel(dryResult.terminal) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="失败原因">{{ displayText(dryResult.errorMessage) }}</el-descriptions-item>
            <el-descriptions-item label="产物" :span="2">
              <template v-if="(dryResult.resultUrls ?? []).length">
                <div v-for="(url, index) in dryResult.resultUrls" :key="index" class="profile-form__url">
                  {{ url }}
                </div>
              </template>
              <span v-else>（未取到产物）</span>
            </el-descriptions-item>
            <el-descriptions-item label="结论" :span="2">{{ dryResult.elapsedHint }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import JsonCodeEditor from "@/components/common/JsonCodeEditor.vue";
import {useEnumOptions} from "@/composables/useEnumOptions";
import {dryRunAsyncTaskProfile, getAsyncTaskProfile, saveAsyncTaskProfile} from "@/api/asyncTaskProfiles";
import type {Dict} from "@/types";
import {displayText} from "@/utils/format";
import {ASYNC_TASK_PROFILE_TEMPLATES, type AsyncTaskProfileTemplate} from "./asyncTaskProfileTemplates";

/**
 * 流式协议集合：只为「置灰不可选」。
 *
 * ⚠ 真正的判据在后端（{@code ModelApiProtocolEnum#streamSupported}），这里置灰只是少一次 400。
 * 之所以允许存在这份拷贝：它是**界面提示**，不是业务判据——判错了后端仍会挡住。
 */
const streamProtocols = new Set(["CHAT_COMPLETIONS", "RESPONSES", "MESSAGES", "COMPLETIONS", "AUDIO_SPEECH"]);

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  profileCode: {type: String, default: ""}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const {optionsOf} = useEnumOptions();
const protocolOptions = computed(() => optionsOf("ModelApiProtocolEnum"));
const templates = ASYNC_TASK_PROFILE_TEMPLATES;

const isCreate = computed(() => !props.profileCode);
const title = computed(() => (isCreate.value ? "新建异步任务档案" : `编辑档案：${props.profileCode}`));

const loading = ref(false);
const saving = ref(false);
const dryRunning = ref(false);
const templateKey = ref("");
const dryRunCreate = ref("");
const dryRunQuery = ref("");
const dryResult = ref<Dict | null>(null);

const enabled = ref(true);

function blankForm(): Dict {
  return {
    profileCode: "",
    protocol: "VIDEO_GENERATIONS",
    queryMethod: "GET",
    queryPath: "",
    taskIdPath: "",
    statusPath: "",
    statusSucceeded: "",
    statusFailed: "",
    statusRunning: "",
    resultPath: "",
    resultUrlField: "",
    errorMessagePath: "",
    resultExpiresDays: 30,
    pollIntervalMs: 5000,
    pollBackoffBaseMs: 1000,
    pollBackoffMaxMs: 30000,
    maxAttempts: 360,
    timeoutMs: 1800000,
    remark: ""
  };
}

const form = reactive<Dict>(blankForm());

const templateHint = computed(() => {
  const found = templates.find((item) => item.key === templateKey.value);
  return found?.hint ?? "";
});

/**
 * 模板预填：只覆盖模板里声明的键。
 *
 * ⚠ 不动 {@code profileCode} 之外的既有输入吗？—— 恰恰相反：模板就是「整组替换」，
 * 留着上一家的路径与表达式会拼出一份谁都不认识的档案。所以这里全量赋值，
 * 但保留用户已经填的编码（那是身份，不是口径）。
 */
function applyTemplate(template: AsyncTaskProfileTemplate) {
  const keepCode = form.profileCode;
  Object.assign(form, blankForm(), template.values);
  if (keepCode) {
    form.profileCode = keepCode;
  } else if (template.profileCode) {
    form.profileCode = template.profileCode;
  }
  enabled.value = true;
  dryResult.value = null;
}

watch(templateKey, (key) => {
  const found = templates.find((item) => item.key === key);
  if (found) {
    applyTemplate(found);
  }
});

watch(
  () => [props.modelValue, props.profileCode] as const,
  ([visible]) => {
    if (!visible) {
      return;
    }
    dryResult.value = null;
    dryRunCreate.value = "";
    dryRunQuery.value = "";
    templateKey.value = "";
    if (props.profileCode) {
      void load();
    } else {
      Object.assign(form, blankForm());
      enabled.value = true;
    }
  },
  {immediate: true}
);

async function load() {
  loading.value = true;
  try {
    const detail = (await getAsyncTaskProfile(props.profileCode)) as Dict;
    Object.assign(form, blankForm(), detail);
    enabled.value = detail.status === 1;
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
}

function payload(): Dict {
  return {...form, status: enabled.value ? 1 : 0};
}

async function onSave() {
  saving.value = true;
  try {
    await saveAsyncTaskProfile(payload());
    ElMessage.success("已保存");
    emit("saved");
    emit("update:modelValue", false);
  } catch {
    // 后端 400 的文案已由拦截器弹出（含具体字段与改法）
  } finally {
    saving.value = false;
  }
}

/**
 * 试跑。
 *
 * ⚠ {@code errors} 非空<b>不算请求失败</b>：它就是要展示给人看的配置问题，
 * 所以这里不 catch 成「操作失败」，而是原样渲染。
 */
async function onDryRun() {
  dryRunning.value = true;
  try {
    dryResult.value = (await dryRunAsyncTaskProfile({
      profile: payload(),
      createResponseJson: dryRunCreate.value,
      queryResponseJson: dryRunQuery.value
    })) as Dict;
  } catch {
    // 网络/权限错误由拦截器提示
  } finally {
    dryRunning.value = false;
  }
}

function terminalLabel(terminal: unknown): string {
  const text = String(terminal ?? "");
  if (text === "SUCCEEDED") {
    return "成功";
  }
  if (text === "FAILED") {
    return "失败";
  }
  if (text === "RUNNING") {
    return "进行中";
  }
  return "未判定";
}

function terminalTagType(terminal: unknown): "success" | "danger" | "warning" | "info" {
  const text = String(terminal ?? "");
  if (text === "SUCCEEDED") {
    return "success";
  }
  if (text === "FAILED") {
    return "danger";
  }
  if (text === "RUNNING") {
    return "warning";
  }
  return "info";
}
</script>

<style scoped>
.profile-form {
  padding-right: 8px;
}

.profile-form__template {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.profile-form__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.profile-form__hint--inline {
  margin-left: 12px;
}

.profile-form__dry-run {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.profile-form__dry-run-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.profile-form__dry-run-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.profile-form__dry-run-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.profile-form__alert {
  margin-bottom: 0;
}

.profile-form__result {
  margin-top: 4px;
}

.profile-form__url {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  word-break: break-all;
}
</style>
