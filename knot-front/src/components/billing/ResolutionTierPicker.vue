<template>
  <div class="tier-picker">
    <el-form-item label="支持档位" label-width="120px">
      <el-select
        :model-value="selectedKeys"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="选择或输入档位，如 720P / 768P"
        style="width: 100%"
        @update:model-value="(value: string[]) => applyKeys(value)"
      >
        <el-option v-for="item in options" :key="item" :label="item" :value="item"/>
      </el-select>
    </el-form-item>
    <div class="tier-picker__actions">
      <el-button size="small" @click="selectCommon">全选常用档位</el-button>
      <el-button size="small" :disabled="!selectedKeys.length" @click="clear">清空</el-button>
      <span class="tier-picker__tip">{{ tip }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {type PropType, computed} from "vue";
import {COMMON_RESOLUTION_OPTIONS, createResolutionRow, normalizeTierKey} from "@/utils/billingResolution";
import type {ResolutionRow} from "@/utils/billingResolution";

/**
 * 档位选择（两步式的第一步：先决定「支持哪些档位」）。
 *
 * <p>⭐ 为什么要有这一步：视频计费的档位价是「档位 × 定价维度」的稀疏矩阵——
 * 管理员真正要先回答的是「这个模型支持 720P 还是 1080P」，而不是「第一行填什么」。
 * 旧交互是「新增一行 → 在行里选档位名 → 再填价」，档位混在价格表里，
 * 新增档位要滚到表尾、删错档位要逐行找，且永远有一行「档位名留空」的僵尸行参与保存判断。</p>
 *
 * <p>⭐ 行是唯一真源：选中的档位就是从 {@code rows} 派生出来的，不另存 {@code selectedKeys}
 * 状态——另存一份就会出现「勾选了但没落库 / 落库了但没勾选」两份可漂移的副本。
 * 勾选 = 建行、取消 = 删行，直接改传入的真实数组引用（父组件的 {@code form.videoResolutionRows}）。</p>
 *
 * <p>档位名必须可自由输入（{@code allow-create}）：厂商档位不可枚举，MiniMax 用 768P、Vidu 用 540P，
 * 任何固定下拉都会让新档位永远配不上价。</p>
 */
const props = defineProps({
  /** 档位行（真实对象引用）：本组件直接在上面增删，不复制一份 */
  rows: {type: Array as PropType<ResolutionRow[]>, required: true},
  /** 下拉建议：只是常用值，不是合法值白名单 */
  options: {type: Array as PropType<string[]>, default: (): string[] => [...COMMON_RESOLUTION_OPTIONS]},
  /** 选择区下方的提示（各使用方按自己的落库口径写） */
  tip: {type: String, default: ""}
});

/** 已选档位：行里有名字的 key，顺序即下方价格表的展示顺序 */
const selectedKeys = computed(() => props.rows.map((row) => normalizeTierKey(row.key)).filter(Boolean));

let sequence = 0;

function nextUid(): string {
  sequence += 1;
  return `tier-picker-${Date.now()}-${sequence}`;
}

/**
 * 勾选变化 -> 行集合对齐：不在选择里的行直接删掉（连带已填单价），缺失的补空行，最后按选择顺序重排。
 *
 * <p>取消勾选即删除是刻意的选择：**未选中的档位不该以隐藏形式留在 {@code config_json} 里**——
 * 那是一份界面上看不见、但计费时会命中的价，等于埋了一颗静默收费的雷。
 * 代价是误点一下要重填，用按钮区的提示文案摊掉这个成本。</p>
 */
function applyKeys(values: string[]) {
  const wanted = [...new Set(values.map(normalizeTierKey).filter(Boolean))];
  for (let i = props.rows.length - 1; i >= 0; i -= 1) {
    if (!wanted.includes(normalizeTierKey(props.rows[i].key))) {
      props.rows.splice(i, 1);
    }
  }
  const known = new Set(props.rows.map((row) => normalizeTierKey(row.key)));
  for (const key of wanted) {
    if (!known.has(key)) {
      props.rows.push(createResolutionRow(key, nextUid()));
    }
  }
  props.rows.sort(
    (a, b) => wanted.indexOf(normalizeTierKey(a.key)) - wanted.indexOf(normalizeTierKey(b.key))
  );
}

/** 常用档位并入当前选择（保留已有顺序与已填价格，不覆盖） */
function selectCommon() {
  applyKeys([...selectedKeys.value, ...props.options]);
}

function clear() {
  applyKeys([]);
}
</script>

<style scoped>
.tier-picker {
  width: 100%;
}

.tier-picker__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: -4px 0 0 120px;
}

.tier-picker__tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
