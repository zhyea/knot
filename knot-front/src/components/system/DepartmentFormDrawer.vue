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
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                  inline-prompt
              />
            </el-form-item>
          </div>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="部门编码" required>
                <el-input v-model="form.deptCode" :disabled="isEdit" maxlength="64" placeholder="请输入部门编码"/>
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

<script setup>
import {computed, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {createDepartment, updateDepartment} from "../../api/departments";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  department: {type: Object, default: null},
  treeOptions: {type: Array, default: () => []},
  defaultParentId: {type: Number, default: null}
});

const emit = defineEmits(["update:modelValue", "saved"]);

const isEdit = computed(() => props.department != null);
const saving = ref(false);
const form = reactive({
  id: null,
  deptCode: "",
  deptName: "",
  parentId: null,
  sortOrder: 0,
  status: 1,
  remark: ""
});

const parentOptions = computed(() => {
  const options = [];
  const walk = (nodes, level = 1, path = []) => {
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

function resetForm() {
  if (props.department) {
    form.id = props.department.id;
    form.deptCode = props.department.deptCode || "";
    form.deptName = props.department.deptName || "";
    form.parentId = props.department.parentId ?? null;
    form.sortOrder = props.department.sortOrder ?? 0;
    form.status = props.department.status ?? 1;
    form.remark = props.department.remark || "";
    return;
  }
  form.id = null;
  form.deptCode = "";
  form.deptName = "";
  form.parentId = props.defaultParentId ?? null;
  form.sortOrder = 0;
  form.status = 1;
  form.remark = "";
}

watch(
    () => [props.modelValue, props.department],
    ([visible]) => {
      if (visible) {
        resetForm();
      }
    },
    {immediate: true}
);

function shouldDisableParent(node) {
  if (!props.department) {
    return countLevel(node) >= 3;
  }
  if (node.id === props.department.id) {
    return true;
  }
  if (containsDepartment(node, props.department.id)) {
    return true;
  }
  return countLevel(node) + countSubtreeHeight(props.department) > 3;
}

function containsDepartment(node, targetId) {
  if (!node) return false;
  if (node.id === targetId) return true;
  return (node.children || []).some((child) => containsDepartment(child, targetId));
}

function countLevel(node) {
  let level = 1;
  let cursor = node;
  const parentMap = new Map();
  const build = (nodes, parentId = null) => {
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

function countSubtreeHeight(node) {
  if (!node) return 1;
  const children = node.children || [];
  if (!children.length) {
    return 1;
  }
  return 1 + Math.max(...children.map((child) => countSubtreeHeight(child)));
}

function findNodeById(nodes, id) {
  for (const node of nodes || []) {
    if (node.id === id) return node;
    const found = findNodeById(node.children, id);
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
      await updateDepartment(form.id, payload);
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

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.section-head--compact {
  justify-content: flex-start;
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
