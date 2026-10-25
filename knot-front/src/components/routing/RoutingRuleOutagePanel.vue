<template>
  <div class="slot-body rule-section">
    <div class="section-head">
      <div>
        <h3>当前摘除目标</h3>
        <p>实时反映本规则下各供应商模型的健康状态（异常摘除 / 探活中）。数据来自网关运行时，操作即时生效。</p>
      </div>
      <el-button text type="primary" :loading="loading" @click="refresh">
        <el-icon>
          <Refresh/>
        </el-icon>
        刷新
      </el-button>
    </div>

    <el-table v-if="items.length" :data="items" border size="small" class="outage-table">
      <el-table-column prop="modelCode" label="模型" min-width="140" show-overflow-tooltip/>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="isEjected(row) ? 'danger' : 'warning'" size="small">
            {{ isEjected(row) ? "摘除中" : "探活中" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" width="90" show-overflow-tooltip/>
      <el-table-column label="窗口计数" width="120">
        <template #default="{ row }">失败 {{ row.failures }} / 慢 {{ row.slows }}</template>
      </el-table-column>
      <el-table-column label="剩余冷却" width="110" align="center">
        <template #default="{ row }">{{ formatCooldown(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ row }">
          <el-button link type="primary" :loading="row._acting" @click="releaseOutage(row)">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="当前无摘除目标" :image-size="60"/>

    <el-divider>手动摘除</el-divider>
    <el-form :inline="true" class="outage-eject-form">
      <el-form-item label="模型" required>
        <el-select
          v-model="ejectForm.modelCode"
          placeholder="选择或输入模型 code"
          filterable
          allow-create
          default-first-option
          clearable
          style="width: 220px"
        >
          <el-option v-for="code in modelCodes" :key="code" :label="code" :value="code"/>
        </el-select>
      </el-form-item>
      <el-form-item label="冷却(秒)">
        <el-input-number v-model="ejectForm.cooldownSec" :min="1" :max="3600" :step="10" style="width: 120px"/>
      </el-form-item>
      <el-form-item label="原因">
        <el-input v-model="ejectForm.reason" placeholder="人工原因（可选）" style="width: 200px"/>
      </el-form-item>
      <el-form-item>
        <el-button type="danger" :loading="ejecting" @click="submitEject">摘除</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import {reactive, ref} from "vue";
import {ElMessage} from "element-plus";
import {Refresh} from "@element-plus/icons-vue";
import {listGatewayOutages, releaseGatewayOutage, ejectGatewayOutage, type GatewayOutageItem} from "@/api/gatewayOutage";

/**
 * B4 管理面：本规则下被异常摘除 / 探活中的供应商模型。
 *
 * 与「规则表单编辑」是**两个抽象层**——这里是网关运行时健康态管理，有独立的 API 域
 * （`@/api/gatewayOutage`）与独立的刷新生命周期，与表单只共享一个 `ruleCode`。
 * 因此不持有 form，也不参与提交链路：`ruleCode` 与「可手动摘除的模型候选」都由父组件传入。
 */
const props = defineProps({
  /** 所属规则编码（后端摘除记录的归属键） */
  ruleCode: {type: String, default: ""},
  /** 手动摘除候选：本规则绑定的「模型」类目标 code（不含模型池） */
  modelCodes: {type: Array as () => string[], default: () => []}
});

type OutageRow = GatewayOutageItem & { _acting?: boolean };

const items = ref<OutageRow[]>([]);
const loading = ref(false);
const ejecting = ref(false);
const ejectForm = reactive({modelCode: "", cooldownSec: 60, reason: ""});

/** 拉取本规则下的摘除 / 探活目标。网关不可达时静默降级为空态（不阻塞表单）。 */
async function refresh() {
  if (!props.ruleCode) {
    return;
  }
  loading.value = true;
  try {
    const rows = await listGatewayOutages({ruleCode: props.ruleCode});
    items.value = (rows || []).map((item) => ({...item, _acting: false}));
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
  }
}

// 何时拉取由父组件决定（打开抽屉/换规则时调 `refresh()`）——刷新时机属编排，不随状态机一起搬过来。
// 不自挂 `onMounted` 拉取：那会把正确性押在「抽屉 destroy-on-close 一定会卸载重挂」上，而该语义
// 在 jsdom 里测不出来，等于用一个测不到的前提换一次少写的调用。
defineExpose({refresh});

/** 处于摘除冷却期（ejectedUntil 未过期）视为「摘除中」，否则「探活中」。 */
function isEjected(row: OutageRow): boolean {
  return row.ejectedUntil > Date.now();
}

function formatCooldown(row: OutageRow): string {
  // 按当前状态取对应截止时间：摘除中看冷却剩余，探活中看探活剩余。
  // 不能取两者的 min——探活行的 ejectedUntil 已过期，min 恒 ≤ 0 会让这一列永远是「—」。
  const remain = (isEjected(row) ? row.ejectedUntil : row.probeUntil) - Date.now();
  if (remain <= 0) {
    return "—";
  }
  const sec = Math.ceil(remain / 1000);
  if (sec < 60) {
    return `${sec}s`;
  }
  return `${Math.floor(sec / 60)}m${sec % 60}s`;
}

async function releaseOutage(row: OutageRow) {
  row._acting = true;
  try {
    await releaseGatewayOutage({ruleCode: props.ruleCode, modelCode: row.modelCode});
    ElMessage.success(`已恢复 ${row.modelCode}`);
    await refresh();
  } catch {
    /* 错误已由全局拦截器提示 */
  } finally {
    row._acting = false;
  }
}

async function submitEject() {
  if (!ejectForm.modelCode) {
    ElMessage.warning("请选择或输入模型 code");
    return;
  }
  ejecting.value = true;
  try {
    await ejectGatewayOutage({
      ruleCode: props.ruleCode,
      modelCode: ejectForm.modelCode,
      cooldownSec: ejectForm.cooldownSec,
      reason: ejectForm.reason || undefined
    });
    ElMessage.success(`已手动摘除 ${ejectForm.modelCode}`);
    ejectForm.modelCode = "";
    ejectForm.reason = "";
    await refresh();
  } catch {
    /* 错误已由全局拦截器提示 */
  } finally {
    ejecting.value = false;
  }
}
</script>

<style scoped>
/* `.rule-section` 的边框/阴影样式由父组件 scoped CSS 命中本组件根节点，无需重复定义 */
.section-head p {
  margin: 5px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .section-head {
    display: block;
  }
}
</style>
