<template>
  <el-drawer
      :model-value="modelValue"
      :title="isEdit ? '编辑部门' : '新建部门'"
      size="40%"
      class="drawer-with-scrollbar"
      destroy-on-close
      @update:model-value="emit('update:modelValue', $event)"
  >
    <el-scrollbar max-height="calc(100vh - 140px)">
      <el-form :model="form" label-width="96px" class="department-form">
        <div class="slot-body department-section">
          <div class="section-head">
            <div>
              <h3>基础信息</h3>
              <p>维护部门编码、名称、层级关系和展示顺序。</p>
            </div>
            <el-form-item label="启用" class="inline-switch">
              <el-switch
                  v-model="form.status"
                  :active-value="EnabledStatus.ENABLED"
                  :inactive-value="EnabledStatus.DISABLED"
                  active-text="启用"
                  inactive-text="禁用"
                  inline-prompt
              />
            </el-form-item>
          </div>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="部门编码" required>
                <el-input v-model="form.deptCode" :disabled="codeLocked" maxlength="64" placeholder="请输入部门编码"/>
                <div v-if="codeLocked" class="form-tip">{{ codeLockHint }}</div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="部门名称" required>
                <el-input v-model="form.deptName" maxlength="100" placeholder="请输入部门名称"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="上级部门">
                <el-select v-model="form.parentId" clearable filterable placeholder="请选择上级部门"
                           style="width: 100%">
                  <el-option
                      v-for="item in parentOptions"
                      :key="item.id"
                      :label="item.label"
                      :value="item.id"
                      :disabled="item.disabled"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序">
                <el-input-number v-model="form.sortOrder" :min="0" :max="9999"/>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="space-line"/>

        <div class="slot-body department-section">
          <div class="section-head section-head--compact">
            <div>
              <h3>补充信息</h3>
              <p>备注用于补充部门职责、归属或其他说明。</p>
            </div>
          </div>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="4" maxlength="255" show-word-limit/>
          </el-form-item>
        </div>
      </el-form>
    </el-scrollbar>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch, type PropType} from "vue";
import {ElMessage} from "element-plus";
import {createDepartment, getDepartment, updateDepartment} from "@/api/departments";
import type {Dict, Row} from "@/types";
import {useCodeBindingLock} from "@/composables/useCodeBindingLock";
import {EnabledStatus} from "@/constants/status";

/** 上级部门下拉项：label 已拼好层级缩进路径 */
interface ParentOption {
  id: number | string;
  label: string;
  disabled: boolean;
}

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 编辑态只接主键：打开时按 id 拉详情，不复用树 / 列表行数据 */
  departmentId: {type: Number as PropType<number | null>, default: null},
  treeOptions: {type: Array as PropType<Row[]>, default: (): Row[] => []},
  defaultParentId: {type: Number as PropType<number | null>, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const isEdit = computed(() => props.departmentId != null);
/** 机构编码引用处数（部门下用户 + 应用数）：>0 时不可改 */
const boundCount = ref(0);
const {codeLocked, codeLockHint} = useCodeBindingLock(() => isEdit.value, () => boundCount.value);
const saving = ref(false);
interface DeptFormState {
  id: number | string | null;
  deptCode: string;
  deptName: string;
  parentId: number | string | null;
  sortOrder: number;
  status: number;
  remark: string;
}

const form = reactive<DeptFormState>({
  id: null,
  deptCode: "",
  deptName: "",
  parentId: null,
  sortOrder: 0,
  status: 1,
  remark: ""
});

const parentOptions = computed(() => {
  const options: ParentOption[] = [];
  const walk = (nodes: Row[], level = 1, path: string[] = []): void => {
    for (const node of nodes || []) {
      const currentPath = [...path, node.deptName];
      options.push({
        id: node.id,
        label: `${"  ".repeat(Math.max(level - 1, 0))}${currentPath.join(" / ")}`,
        disabled: shouldDisableParent(node)
      });
      walk(node.children, level + 1, currentPath);
    }
  };
  walk(props.treeOptions);
  return options;
});

function resetForm(detail: Dict | null): void {
  form.id = detail?.id ?? null;
  form.deptCode = detail?.deptCode || "";
  form.deptName = detail?.deptName || "";
  form.parentId = detail?.parentId ?? props.defaultParentId ?? null;
  form.sortOrder = detail?.sortOrder ?? 0;
  form.status = detail?.status ?? 1;
  form.remark = detail?.remark || "";
  boundCount.value = Number(detail?.boundCount ?? 0);
}

watch(
    () => [props.modelValue, props.departmentId] as const,
    async ([visible]) => {
      if (!visible) return;
      if (props.departmentId == null) {
        resetForm(null);
        return;
      }
      let detail: Dict;
      try {
        const loaded = await getDepartment(props.departmentId);
        if (!loaded) throw new Error("部门详情为空");
        detail = loaded as Dict;
      } catch {
        ElMessage.error("部门详情加载失败");
        emit("update:modelValue", false);
        return;
      }
      resetForm(detail);
    },
    {immediate: true}
);

function shouldDisableParent(node: Row): boolean {
  if (props.departmentId == null) {
    return countLevel(node) >= 3;
  }
  if (Number(node.id) === props.departmentId) {
    return true;
  }
  if (containsDepartment(node, props.departmentId)) {
    return true;
  }
  // 编辑对象的子树只从 /tree 端点取，不复用触发编辑的列表行
  const current = props.departmentId == null ? null : findNodeById(props.treeOptions, props.departmentId);
  return countLevel(node) + countSubtreeHeight(current) > 3;
}

function containsDepartment(node: Row, targetId: number | null): boolean {
  if (!node) return false;
  if (Number(node.id) === targetId) return true;
  return (node.children || []).some((child: Row) => containsDepartment(child, targetId));
}

function countLevel(node: Row): number {
  let level = 1;
  let cursor: Row | null = node;
  const parentMap = new Map<unknown, unknown>();
  const build = (nodes: Row[], parentId: unknown = null): void => {
    for (const item of nodes || []) {
      parentMap.set(item.id, parentId);
      build(item.children, item.id);
    }
  };
  build(props.treeOptions);
  while (cursor && parentMap.get(cursor.id) != null) {
    level += 1;
    const parentId = parentMap.get(cursor.id);
    cursor = findNodeById(props.treeOptions, parentId);
  }
  return level;
}

function countSubtreeHeight(node: Row | null) {
  if (!node) return 1;
  const children = node.children || [];
  if (!children.length) {
    return 1;
  }
  return 1 + Math.max(...children.map((child: Row) => countSubtreeHeight(child)));
}

function findNodeById(nodes: Row[], id: unknown): Row | null {
  for (const node of nodes || []) {
    if (node.id === id) return node;
    const found: Row | null = findNodeById(node.children, id);
    if (found) return found;
  }
  return null;
}

async function submit() {
  if (!form.deptCode?.trim()) {
    ElMessage.warning("请填写部门编码");
    return;
  }
  if (!form.deptName?.trim()) {
    ElMessage.warning("请填写部门名称");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      id: form.id,
      deptCode: form.deptCode.trim(),
      deptName: form.deptName.trim(),
      parentId: form.parentId ?? null,
      sortOrder: form.sortOrder ?? 0,
      status: form.status ?? 1,
      remark: form.remark?.trim() || null
    };
    if (isEdit.value) {
      await updateDepartment(form.id!, payload);
      ElMessage.success("已保存");
    } else {
      await createDepartment(payload);
      ElMessage.success("已创建");
    }
    emit("update:modelValue", false);
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.department-form {
  padding-right: 12px;
  padding-bottom: 8px;
}

.department-form :deep(.el-input-number) {
  width: 100%;
}

.department-section {
  border-color: #e4e7ed;
}

.section-head--compact {
  justify-content: flex-start;
}

</style>
