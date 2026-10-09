import {computed, reactive, ref, type Ref} from "vue";
import {ElMessage} from "element-plus";
import type {Dict, ResourceField, ResourceSubmitter, Row, RowAction} from "@/types";
import {useAutoQuery} from "./useAutoQuery";
import {usePageList} from "./usePageList";
import {
  createAuthorizationApiBinding,
  createAuthorizationMenu,
  createAuthorizationModule,
  createAuthorizationPermission,
  deleteAuthorizationApiBinding,
  deleteAuthorizationMenu,
  deleteAuthorizationModule,
  deleteAuthorizationPermission,
  deleteAuthorizationRole,
  getRoleAuthorizationSnapshot,
  listAuthorizationApiBindings,
  listAuthorizationMenus,
  listAuthorizationModules,
  listAuthorizationPermissions,
  listAuthorizationRoles,
  saveRolePermissions,
  updateAuthorizationApiBinding,
  updateAuthorizationApiBindingStatus,
  updateAuthorizationMenu,
  updateAuthorizationMenuStatus,
  updateAuthorizationModule,
  updateAuthorizationModuleStatus,
  updateAuthorizationPermission,
  updateAuthorizationPermissionStatus
} from "@/api/authorizations";
import {listOperationLogsByEntity} from "@/api/operationLogs";

/** 权限分组树里的菜单节点 */
interface PermissionMenuNode {
  id: unknown;
  parentId: unknown;
  moduleId: unknown;
  menuName: string;
  sortOrder: number;
  permissions: Row[];
  children: PermissionMenuNode[];
}

/** 权限分组树里的模块分组 */
interface PermissionModuleGroup {
  moduleId: unknown;
  moduleName: string;
  moduleSortOrder: number;
  menuMap: Map<unknown, PermissionMenuNode>;
  rootMenus: PermissionMenuNode[];
  unassignedPermissions: Row[];
}

export function useAuthorizationManagement() {
  const activeSection = ref("roles");
  const activeResourceTab = ref("modules");
  const roleKeyword = ref("");
  const selectedRole = ref<Row | null>(null);
  const grantedPermissionIds = ref<number[]>([]);
  const permissionKeyword = ref("");
  const grantSaving = ref(false);

  const roleDialogVisible = ref(false);
  const roleGrantDrawerVisible = ref(false);
  const editingRole = ref<Row | null>(null);

  const resourceDialogVisible = ref(false);
  const resourceDialogTitle = ref("");
  const resourceDialogForm = ref<Dict>({});
  const resourceDialogFields = ref<ResourceField[]>([]);
  const resourceDialogSubmitter = ref<ResourceSubmitter>(async () => ({}));
  const resourceType = ref("");

  // 操作日志抽屉（查看某条授权资源的变更记录，统一走 POST /api/operation-logs/list）
  const logDrawerVisible = ref(false);
  const logTitle = ref("");
  const logLoadFn = ref<(() => Promise<Row[]>)>(async () => []);

  function openLogDrawer(loadFn: () => Promise<Row[]>, title: string) {
    logLoadFn.value = loadFn;
    logTitle.value = title;
    logDrawerVisible.value = true;
  }

  const moduleLoading = ref(false);
  const menuLoading = ref(false);
  const permissionLoading = ref(false);
  const apiBindingLoading = ref(false);
  const moduleCatalogLoading = ref(false);
  const menuCatalogLoading = ref(false);
  const moduleStatusUpdatingId = ref(null);
  const menuStatusUpdatingId = ref(null);
  const permissionStatusUpdatingId = ref(null);
  const apiBindingStatusUpdatingId = ref(null);

  const modules = ref<Row[]>([]);
  const menus = ref<Row[]>([]);
  const moduleCatalog = ref<Row[]>([]);
  const menuCatalog = ref<Row[]>([]);
  const permissionCatalog = ref<Row[]>([]);
  const permissions = ref<Row[]>([]);
  const apiBindings = ref<Row[]>([]);

  const moduleQuery = reactive<Dict>({ keyword: "" });
  const menuQuery = reactive<Dict>({ keyword: "", moduleId: null });
  const permissionQuery = reactive<Dict>({ keyword: "", moduleId: null, menuId: null, permissionType: "" });
  const apiBindingQuery = reactive<Dict>({ keyword: "", httpMethod: "", permissionId: null });

  const {
    rows,
    loading,
    total,
    pageNum,
    pageSize,
    extra,
    load,
    onPageChange,
    onSizeChange,
    resetPage
  } = usePageList(listAuthorizationRoles);
  const { pauseAutoQuery: pauseRoleAutoQuery } = useAutoQuery(roleKeyword, handleRoleQuery, { deep: false });
  const { pauseAutoQuery: pauseModuleAutoQuery } = useAutoQuery(moduleQuery, loadModules);
  const { pauseAutoQuery: pauseMenuAutoQuery } = useAutoQuery(menuQuery, loadMenus);
  const { pauseAutoQuery: pausePermissionAutoQuery } = useAutoQuery(permissionQuery, loadPermissions);
  const { pauseAutoQuery: pauseApiBindingAutoQuery } = useAutoQuery(apiBindingQuery, loadApiBindings);

  const moduleOptions = computed(() =>
    moduleCatalog.value.map((item) => ({ label: item.moduleName, value: item.id }))
  );

  const menuTreeOptions = computed(() => buildMenuTreeOptions(menuCatalog.value));

  const menuOptions = computed(() => flattenTreeOptions(menuTreeOptions.value));

  const permissionOptions = computed(() =>
    permissionCatalog.value.map((item) => ({
      label: `${item.permissionName} (${item.permissionCode})`,
      value: item.id
    }))
  );

  const permissionGroups = computed(() => {
    const keyword = permissionKeyword.value.trim().toLowerCase();
    const moduleMap = new Map<unknown, PermissionModuleGroup>(
      modules.value.map((item) => [
        item.id,
        {
          moduleId: item.id,
          moduleName: item.moduleName,
          moduleSortOrder: item.sortOrder ?? Number.MAX_SAFE_INTEGER,
          menuMap: new Map<unknown, PermissionMenuNode>(),
          rootMenus: [] as PermissionMenuNode[],
          unassignedPermissions: [] as Row[]
        }
      ])
    );
    const menuMap = new Map<unknown, PermissionMenuNode>(
      menus.value.map((item) => [
        item.id,
        {
          id: item.id,
          parentId: item.parentId,
          moduleId: item.moduleId,
          menuName: item.menuName,
          sortOrder: item.sortOrder ?? Number.MAX_SAFE_INTEGER,
          permissions: [] as Row[],
          children: [] as PermissionMenuNode[]
        }
      ])
    );

    for (const moduleNode of moduleMap.values()) {
      moduleNode.menuMap = new Map();
    }

    for (const menuNode of menuMap.values()) {
      const moduleNode = moduleMap.get(menuNode.moduleId) || ensurePermissionModuleGroup(moduleMap, menuNode.moduleId, "未归属模块");
      moduleNode.menuMap.set(menuNode.id, menuNode);
    }

    for (const menuNode of menuMap.values()) {
      const moduleNode = moduleMap.get(menuNode.moduleId);
      if (!moduleNode) {
        continue;
      }
      const parentNode = menuNode.parentId ? moduleNode.menuMap.get(menuNode.parentId) : null;
      if (parentNode) {
        parentNode.children.push(menuNode);
      } else {
        moduleNode.rootMenus.push(menuNode);
      }
    }

    for (const permission of permissionCatalog.value) {
      const searchText = [
        permission.permissionCode,
        permission.permissionName,
        permission.moduleName,
        permission.menuName
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      if (keyword && !searchText.includes(keyword)) {
        continue;
      }

      const moduleNode = ensurePermissionModuleGroup(
        moduleMap,
        permission.moduleId || 0,
        permission.moduleName || "未归属模块"
      );
      const targetMenu = permission.menuId ? moduleNode.menuMap.get(permission.menuId) : null;
      if (targetMenu) {
        targetMenu.permissions.push(permission);
      } else {
        moduleNode.unassignedPermissions.push(permission);
      }
    }

    return Array.from(moduleMap.values())
      .map((group) => ({
        moduleId: group.moduleId,
        moduleName: group.moduleName,
        moduleSortOrder: group.moduleSortOrder,
        menus: sortPermissionMenuNodes(group.rootMenus),
        unassignedPermissions: group.unassignedPermissions.sort(comparePermissionByName)
      }))
      .filter((group) => group.menus.length > 0 || group.unassignedPermissions.length > 0)
      .sort((a, b) => a.moduleSortOrder - b.moduleSortOrder || `${a.moduleName}`.localeCompare(`${b.moduleName}`, "zh-Hans-CN"));
  });

  const moduleFilters = computed(() => [
    { key: "keyword", label: "关键字", placeholder: "按模块编码、名称筛选" }
  ]);

  const menuFilters = computed(() => [
    { key: "keyword", label: "关键字", placeholder: "按菜单编码、名称、路由筛选" },
    { key: "moduleId", label: "所属模块", type: "select", options: moduleOptions.value, placeholder: "全部模块", width: "320px" }
  ]);

  const permissionFilters = computed(() => [
    { key: "keyword", label: "关键字", placeholder: "按权限编码、名称筛选" },
    { key: "moduleId", label: "所属模块", type: "select", options: moduleOptions.value, placeholder: "全部模块", width: "320px" },
    { key: "menuId", label: "所属菜单", type: "select", options: menuOptions.value, placeholder: "全部菜单", width: "360px" },
    {
      key: "permissionType",
      label: "权限类型",
      type: "select",
      options: [
        { label: "PAGE", value: "PAGE" },
        { label: "API", value: "API" }
      ],
      placeholder: "全部类型",
      width: "220px"
    }
  ]);

  const apiBindingFilters = computed(() => [
    { key: "keyword", label: "关键字", placeholder: "按路径、控制器、方法筛选" },
    {
      key: "httpMethod",
      label: "HTTP",
      type: "select",
      options: ["GET", "POST", "PUT", "DELETE", "PATCH"].map((item) => ({ label: item, value: item })),
      placeholder: "全部方法",
      width: "220px"
    },
    { key: "permissionId", label: "绑定权限", type: "select", options: permissionOptions.value, placeholder: "全部权限", width: "360px" }
  ]);

  const moduleColumns = [
    { prop: "moduleCode", label: "模块编码", minWidth: 140 },
    { prop: "moduleName", label: "模块名称", minWidth: 140 },
    { prop: "icon", label: "图标", minWidth: 120 },
    { prop: "sortOrder", label: "排序", width: 90, align: "center" },
    { label: "状态", width: 96, align: "center", slot: "statusSwitch" }
  ];

  const menuColumns = [
    { prop: "menuCode", label: "菜单编码", minWidth: 150 },
    { prop: "menuName", label: "菜单名称", minWidth: 140 },
    { prop: "moduleName", label: "所属模块", minWidth: 140 },
    { prop: "routePath", label: "路由路径", minWidth: 180 },
    { prop: "componentKey", label: "组件键", minWidth: 180 },
    { label: "上级菜单", minWidth: 140, slot: "parentLabel" },
    { label: "状态", width: 96, align: "center", slot: "statusSwitch" }
  ];

  const permissionColumns = [
    { prop: "permissionCode", label: "权限编码", minWidth: 180 },
    { prop: "permissionName", label: "权限名称", minWidth: 160 },
    { prop: "permissionType", label: "类型", width: 90, align: "center" },
    { prop: "moduleName", label: "所属模块", minWidth: 140 },
    { prop: "menuName", label: "所属菜单", minWidth: 140 },
    { label: "状态", width: 96, align: "center", slot: "statusSwitch" }
  ];

  const apiBindingColumns = [
    { prop: "permissionName", label: "权限名称", minWidth: 160 },
    { prop: "permissionCode", label: "权限编码", minWidth: 180 },
    { prop: "httpMethod", label: "HTTP", width: 90, align: "center" },
    { prop: "pathPattern", label: "路径模式", minWidth: 220 },
    { prop: "controllerClass", label: "控制器", minWidth: 180 },
    { label: "状态", width: 96, align: "center", slot: "statusSwitch" }
  ];

  async function initializePage() {
    await Promise.all([
      load(),
      loadModuleCatalog(),
      loadMenuCatalog(),
      loadModules(),
      loadMenus(),
      loadPermissionCatalog(),
      loadPermissions(),
      loadApiBindings()
    ]);
    await selectFirstRole();
  }

  async function initializeRoleSection() {
    await Promise.all([
      load(),
      loadModuleCatalog(),
      loadMenuCatalog(),
      loadModules(),
      loadMenus(),
      loadPermissionCatalog()
    ]);
    await selectFirstRole();
  }

  async function initializeResourceSection() {
    await Promise.all([
      loadModuleCatalog(),
      loadMenuCatalog(),
      loadModules(),
      loadMenus(),
      loadPermissionCatalog(),
      loadPermissions(),
      loadApiBindings()
    ]);
  }

  async function handleRoleQuery() {
    await pauseRoleAutoQuery(async () => {
      extra.keyword = roleKeyword.value.trim() || undefined;
      await resetPage();
      await selectFirstRole();
    });
  }

  async function resetRoleQuery() {
    await pauseRoleAutoQuery(async () => {
      roleKeyword.value = "";
      extra.keyword = undefined;
      await resetPage();
      await selectFirstRole();
    });
  }

  async function handleRolePageChange(page: number) {
    await onPageChange(page);
    await selectFirstRole();
  }

  async function handleRoleSizeChange(size: number) {
    await onSizeChange(size);
    await selectFirstRole();
  }

  async function handleRoleSelect(role: Row | null) {
    if (!role?.id) {
      selectedRole.value = null;
      grantedPermissionIds.value = [];
      return;
    }
    await loadRoleSnapshot(role.id);
  }

  async function selectFirstRole() {
    if (!rows.value.length) {
      selectedRole.value = null;
      grantedPermissionIds.value = [];
      return;
    }
      const currentId = selectedRole.value?.id;
    const roleId = currentId != null && rows.value.some((item) => item.id === currentId)
      ? currentId
      : rows.value[0].id;
    await loadRoleSnapshot(roleId);
  }

  async function loadRoleSnapshot(roleId: number | string) {
    const snapshot = await getRoleAuthorizationSnapshot(roleId);
    selectedRole.value = rows.value.find((item) => item.id === roleId) || snapshot?.roles?.[0] || null;
    grantedPermissionIds.value = snapshot?.grantedPermissionIds || [];
  }

  function openRoleCreate() {
    editingRole.value = null;
    roleDialogVisible.value = true;
  }

  function handleRoleAction(action: string, row: Row) {
    if (action === "grant") {
      openRoleGrant(row);
      return;
    }
    if (action === "log") {
      openLogDrawer(
        () => listOperationLogsByEntity({ module: "authorization", entityType: "Role", entityId: row.id }),
        `角色操作日志 - ${row.name || row.code || row.id}`
      );
      return;
    }
    if (action === "edit") {
      editingRole.value = row;
      roleDialogVisible.value = true;
      return;
    }
    if (action === "delete") {
      removeRole(row);
    }
  }

  async function removeRole(row: Row) {
    await deleteAuthorizationRole(row.id);
    ElMessage.success("删除成功");
    await resetPage();
    await selectFirstRole();
  }

  async function onRoleSaved(saved: Row | null) {
    await resetPage();
    if (saved?.id) {
      await loadRoleSnapshot(saved.id);
    } else {
      await selectFirstRole();
    }
  }

  async function openRoleGrant(row: Row) {
    if (!row?.id) {
      return;
    }
    await loadRoleSnapshot(row.id);
    roleGrantDrawerVisible.value = true;
  }

  function togglePermission(permissionId: number, checked: boolean) {
    const next = new Set(grantedPermissionIds.value);
    if (checked) {
      next.add(permissionId);
    } else {
      next.delete(permissionId);
    }
    grantedPermissionIds.value = Array.from(next).sort((a, b) => a - b);
  }

  function handlePermissionKeywordChange(value: string) {
    permissionKeyword.value = value || "";
  }

  async function saveRolePermissionBindings() {
    if (!selectedRole.value?.id) {
      ElMessage.warning("请先选择角色");
      return;
    }
    grantSaving.value = true;
    try {
      const snapshot = await saveRolePermissions(selectedRole.value.id, grantedPermissionIds.value);
      grantedPermissionIds.value = snapshot?.grantedPermissionIds || [];
      ElMessage.success("角色授权已保存");
    } finally {
      grantSaving.value = false;
    }
  }

  async function loadModules() {
    moduleLoading.value = true;
    try {
      modules.value = await listAuthorizationModules(cleanQuery(moduleQuery));
    } finally {
      moduleLoading.value = false;
    }
  }

  async function loadModuleCatalog() {
    moduleCatalogLoading.value = true;
    try {
      moduleCatalog.value = await listAuthorizationModules({});
    } finally {
      moduleCatalogLoading.value = false;
    }
  }

  async function loadMenus() {
    menuLoading.value = true;
    try {
      menus.value = await listAuthorizationMenus(cleanQuery(menuQuery));
    } finally {
      menuLoading.value = false;
    }
  }

  async function loadMenuCatalog() {
    menuCatalogLoading.value = true;
    try {
      menuCatalog.value = await listAuthorizationMenus({});
    } finally {
      menuCatalogLoading.value = false;
    }
  }

  async function loadPermissionCatalog() {
    const data = await listAuthorizationPermissions({});
    permissionCatalog.value = Array.isArray(data) ? data : [];
  }

  async function loadPermissions() {
    permissionLoading.value = true;
    try {
      permissions.value = await listAuthorizationPermissions(cleanQuery(permissionQuery));
    } finally {
      permissionLoading.value = false;
    }
  }

  async function loadApiBindings() {
    apiBindingLoading.value = true;
    try {
      apiBindings.value = await listAuthorizationApiBindings(cleanQuery(apiBindingQuery));
    } finally {
      apiBindingLoading.value = false;
    }
  }

  function handleModuleQuery() {
    return pauseModuleAutoQuery(() => loadModules());
  }

  function handleMenuQuery() {
    return pauseMenuAutoQuery(() => loadMenus());
  }

  function handlePermissionQuery() {
    return pausePermissionAutoQuery(() => loadPermissions());
  }

  function handleApiBindingQuery() {
    return pauseApiBindingAutoQuery(() => loadApiBindings());
  }

  function updateModuleFilter(key: string, value: unknown) {
    moduleQuery[key] = value || "";
  }

  function updateMenuFilter(key: string, value: unknown) {
    menuQuery[key] = value || null;
  }

  function updatePermissionFilter(key: string, value: unknown) {
    permissionQuery[key] = value || "";
  }

  function updateApiBindingFilter(key: string, value: unknown) {
    apiBindingQuery[key] = value || "";
  }

  async function resetModuleFilters() {
    await pauseModuleAutoQuery(async () => {
      moduleQuery.keyword = "";
      await loadModules();
    });
  }

  async function resetMenuFilters() {
    await pauseMenuAutoQuery(async () => {
      menuQuery.keyword = "";
      menuQuery.moduleId = null;
      await loadMenus();
    });
  }

  async function resetPermissionFilters() {
    await pausePermissionAutoQuery(async () => {
      permissionQuery.keyword = "";
      permissionQuery.moduleId = null;
      permissionQuery.menuId = null;
      permissionQuery.permissionType = "";
      await loadPermissions();
    });
  }

  async function resetApiBindingFilters() {
    await pauseApiBindingAutoQuery(async () => {
      apiBindingQuery.keyword = "";
      apiBindingQuery.httpMethod = "";
      apiBindingQuery.permissionId = null;
      await loadApiBindings();
    });
  }

  function openModuleCreate() {
    resourceType.value = "module";
    resourceDialogTitle.value = "新建模块";
    resourceDialogForm.value = {};
    resourceDialogFields.value = [
      { key: "moduleCode", label: "模块编码", required: true, placeholder: "例如: system" },
      { key: "moduleName", label: "模块名称", required: true, placeholder: "请输入模块名称" },
      { key: "icon", label: "图标", placeholder: "例如: Setting" },
      { key: "sortOrder", label: "排序", type: "number", defaultValue: 0 }
    ];
    resourceDialogSubmitter.value = (payload) => createAuthorizationModule(payload);
    resourceDialogVisible.value = true;
  }

  function handleModuleAction(action: string, row: Row) {
    if (action === "log") {
      openLogDrawer(
        () => listOperationLogsByEntity({ module: "authorization", entityType: "Module", entityId: row.id }),
        `模块操作日志 - ${row.moduleName || row.moduleCode || row.id}`
      );
      return;
    }
    if (action === "edit") {
      resourceType.value = "module";
      resourceDialogTitle.value = "编辑模块";
      resourceDialogForm.value = row;
      resourceDialogFields.value = [
        { key: "moduleCode", label: "模块编码", required: true },
        { key: "moduleName", label: "模块名称", required: true },
        { key: "icon", label: "图标" },
        { key: "sortOrder", label: "排序", type: "number", defaultValue: 0 }
      ];
      resourceDialogSubmitter.value = (payload) => updateAuthorizationModule(row.id, payload);
      resourceDialogVisible.value = true;
      return;
    }
    if (action === "delete") {
      deleteAuthorizationModule(row.id).then(async () => {
        ElMessage.success("删除成功");
        await Promise.all([loadModules(), loadModuleCatalog()]);
      });
    }
  }

  function openMenuCreate() {
    resourceType.value = "menu";
    resourceDialogTitle.value = "新建菜单";
    resourceDialogForm.value = {};
    resourceDialogFields.value = [
      { key: "moduleId", label: "所属模块", required: true, type: "select", options: moduleOptions.value, placeholder: "请选择模块" },
      { key: "parentId", label: "上级菜单", type: "tree-select", options: menuTreeOptions.value, placeholder: "可选" },
      { key: "menuCode", label: "菜单编码", required: true },
      { key: "menuName", label: "菜单名称", required: true },
      { key: "routePath", label: "路由路径" },
      { key: "componentKey", label: "组件键" },
      { key: "icon", label: "图标" },
      { key: "sortOrder", label: "排序", type: "number", defaultValue: 0 }
    ];
    resourceDialogSubmitter.value = (payload) => createAuthorizationMenu(normalizeNullableFields(payload, ["parentId"]));
    resourceDialogVisible.value = true;
  }

  function handleMenuAction(action: string, row: Row) {
    if (action === "log") {
      openLogDrawer(
        () => listOperationLogsByEntity({ module: "authorization", entityType: "Menu", entityId: row.id }),
        `菜单操作日志 - ${row.menuName || row.menuCode || row.id}`
      );
      return;
    }
    if (action === "edit") {
      resourceType.value = "menu";
      resourceDialogTitle.value = "编辑菜单";
      resourceDialogForm.value = row;
      resourceDialogFields.value = [
        { key: "moduleId", label: "所属模块", required: true, type: "select", options: moduleOptions.value },
        { key: "parentId", label: "上级菜单", type: "tree-select", options: buildMenuTreeOptions(menuCatalog.value, row.id) },
        { key: "menuCode", label: "菜单编码", required: true },
        { key: "menuName", label: "菜单名称", required: true },
        { key: "routePath", label: "路由路径" },
        { key: "componentKey", label: "组件键" },
        { key: "icon", label: "图标" },
        { key: "sortOrder", label: "排序", type: "number", defaultValue: 0 }
      ];
      resourceDialogSubmitter.value = (payload) =>
        updateAuthorizationMenu(row.id, normalizeNullableFields(payload, ["parentId"]));
      resourceDialogVisible.value = true;
      return;
    }
    if (action === "delete") {
      deleteAuthorizationMenu(row.id).then(async () => {
        ElMessage.success("删除成功");
        await Promise.all([loadMenus(), loadMenuCatalog(), loadPermissions(), loadPermissionCatalog()]);
      });
    }
  }

  function openPermissionCreate() {
    resourceType.value = "permission";
    resourceDialogTitle.value = "新建权限";
    resourceDialogForm.value = {};
    resourceDialogFields.value = [
      { key: "permissionCode", label: "权限编码", required: true },
      { key: "permissionName", label: "权限名称", required: true },
      {
        key: "permissionType",
        label: "权限类型",
        required: true,
        type: "select",
        options: [
          { label: "PAGE", value: "PAGE" },
          { label: "API", value: "API" }
        ],
        defaultValue: "API"
      },
      { key: "moduleId", label: "所属模块", required: true, type: "select", options: moduleOptions.value },
      { key: "menuId", label: "所属菜单", type: "tree-select", options: menuTreeOptions.value, placeholder: "可选" },
      { key: "builtIn", label: "系统内置", type: "select", options: yesNoOptions(), defaultValue: 0 },
      { key: "remark", label: "备注", type: "textarea" }
    ];
    resourceDialogSubmitter.value = (payload) => createAuthorizationPermission(normalizeNullableFields(payload, ["menuId"]));
    resourceDialogVisible.value = true;
  }

  function handlePermissionAction(action: string, row: Row) {
    if (action === "log") {
      openLogDrawer(
        () => listOperationLogsByEntity({ module: "authorization", entityType: "Permission", entityId: row.id }),
        `权限操作日志 - ${row.permissionName || row.permissionCode || row.id}`
      );
      return;
    }
    if (action === "edit") {
      resourceType.value = "permission";
      resourceDialogTitle.value = "编辑权限";
      resourceDialogForm.value = row;
      resourceDialogFields.value = [
        { key: "permissionCode", label: "权限编码", required: true },
        { key: "permissionName", label: "权限名称", required: true },
        {
          key: "permissionType",
          label: "权限类型",
          required: true,
          type: "select",
          options: [
            { label: "PAGE", value: "PAGE" },
            { label: "API", value: "API" }
          ]
        },
        { key: "moduleId", label: "所属模块", required: true, type: "select", options: moduleOptions.value },
        { key: "menuId", label: "所属菜单", type: "tree-select", options: menuTreeOptions.value, placeholder: "可选" },
        { key: "builtIn", label: "系统内置", type: "select", options: yesNoOptions(), defaultValue: 0 },
        { key: "remark", label: "备注", type: "textarea" }
      ];
      resourceDialogSubmitter.value = (payload) =>
        updateAuthorizationPermission(row.id, normalizeNullableFields(payload, ["menuId"]));
      resourceDialogVisible.value = true;
      return;
    }
    if (action === "delete") {
      deleteAuthorizationPermission(row.id).then(async () => {
        ElMessage.success("删除成功");
        await Promise.all([loadPermissions(), loadPermissionCatalog(), loadApiBindings()]);
        if (selectedRole.value?.id) {
          await loadRoleSnapshot(selectedRole.value.id);
        }
      });
    }
  }

  function openApiBindingCreate() {
    resourceType.value = "binding";
    resourceDialogTitle.value = "新建 API 绑定";
    resourceDialogForm.value = {};
    resourceDialogFields.value = [
      { key: "permissionId", label: "绑定权限", required: true, type: "select", options: permissionOptions.value },
      {
        key: "httpMethod",
        label: "HTTP 方法",
        required: true,
        type: "select",
        options: ["GET", "POST", "PUT", "DELETE", "PATCH"].map((item) => ({ label: item, value: item })),
        defaultValue: "GET"
        },
        { key: "pathPattern", label: "路径模式", required: true, placeholder: "例如: /api/system/authorizations/modules" },
        { key: "controllerClass", label: "控制器" }
      ];
    resourceDialogSubmitter.value = (payload) => createAuthorizationApiBinding(payload);
    resourceDialogVisible.value = true;
  }

  function handleApiBindingAction(action: string, row: Row) {
    if (action === "log") {
      openLogDrawer(
        () => listOperationLogsByEntity({ module: "authorization", entityType: "ApiPermissionBinding", entityId: row.id }),
        `API 绑定操作日志 - ${row.pathPattern || row.id}`
      );
      return;
    }
    if (action === "edit") {
      resourceType.value = "binding";
      resourceDialogTitle.value = "编辑 API 绑定";
      resourceDialogForm.value = row;
      resourceDialogFields.value = [
        { key: "permissionId", label: "绑定权限", required: true, type: "select", options: permissionOptions.value },
        {
          key: "httpMethod",
          label: "HTTP 方法",
          required: true,
          type: "select",
          options: ["GET", "POST", "PUT", "DELETE", "PATCH"].map((item) => ({ label: item, value: item }))
          },
          { key: "pathPattern", label: "路径模式", required: true },
          { key: "controllerClass", label: "控制器" }
        ];
      resourceDialogSubmitter.value = (payload) => updateAuthorizationApiBinding(row.id, payload);
      resourceDialogVisible.value = true;
      return;
    }
    if (action === "delete") {
      deleteAuthorizationApiBinding(row.id).then(async () => {
        ElMessage.success("删除成功");
        await loadApiBindings();
      });
    }
  }

  async function onResourceSaved() {
    if (resourceType.value === "module") {
      await Promise.all([loadModules(), loadModuleCatalog()]);
      return;
    }
    if (resourceType.value === "menu") {
      await Promise.all([loadMenus(), loadMenuCatalog(), loadPermissions(), loadPermissionCatalog()]);
      return;
    }
    if (resourceType.value === "permission") {
      await Promise.all([loadPermissions(), loadPermissionCatalog(), loadApiBindings()]);
      if (selectedRole.value?.id) {
        await loadRoleSnapshot(selectedRole.value.id);
      }
      return;
    }
    if (resourceType.value === "binding") {
      await loadApiBindings();
    }
  }

  function resolveMenuName(menuId: unknown): string {
    if (!menuId) {
      return "";
    }
    return menuCatalog.value.find((item) => item.id === menuId)?.menuName || "";
  }

  function beforePermissionDelete(row: Row): Partial<RowAction> {
    if (!row?.builtIn) {
      return {};
    }
    return {
      disabled: true,
      confirm: false
    };
  }

  async function changeResourceStatus(
    row: Row,
    enabled: boolean,
    loadingRef: Ref<unknown>,
    updateApi: (id: number | string, enabled: boolean) => Promise<unknown>,
    reload: () => Promise<unknown>
  ) {
    if (!row?.id) {
      return;
    }
    const previousStatus = row.status || "ENABLED";
    const nextStatus = enabled ? "ENABLED" : "DISABLED";
    if (previousStatus === nextStatus) {
      return;
    }
    loadingRef.value = row.id;
    row.status = nextStatus;
    try {
      await updateApi(row.id, enabled);
      ElMessage.success(enabled ? "已启用" : "已禁用");
      await reload();
    } catch {
      row.status = previousStatus;
    } finally {
      loadingRef.value = null;
    }
  }

  async function handleModuleStatusChange(row: Row, enabled: boolean) {
    await changeResourceStatus(row, enabled, moduleStatusUpdatingId, updateAuthorizationModuleStatus, loadModules);
  }

  async function handleMenuStatusChange(row: Row, enabled: boolean) {
    await changeResourceStatus(row, enabled, menuStatusUpdatingId, updateAuthorizationMenuStatus, loadMenus);
  }

  async function handlePermissionStatusChange(row: Row, enabled: boolean) {
    await changeResourceStatus(row, enabled, permissionStatusUpdatingId, updateAuthorizationPermissionStatus, loadPermissions);
  }

  async function handleApiBindingStatusChange(row: Row, enabled: boolean) {
    await changeResourceStatus(row, enabled, apiBindingStatusUpdatingId, updateAuthorizationApiBindingStatus, loadApiBindings);
  }

  function yesNoOptions() {
    return [
      { label: "否", value: 0 },
      { label: "是", value: 1 }
    ];
  }

  function cleanQuery(query: Dict): Dict {
    const payload: Dict = {};
    Object.entries(query || {}).forEach(([key, value]) => {
      if (value !== "" && value !== null && value !== undefined) {
        payload[key] = value;
      }
    });
    return payload;
  }

  function normalizeNullableFields(payload: Dict, keys: string[]): Dict {
    const result = { ...payload };
    for (const key of keys) {
      if (result[key] === "" || result[key] === undefined) {
        result[key] = null;
      }
    }
    return result;
  }

  function buildMenuTreeOptions(sourceMenus: Row[], excludeId: unknown = null) {
    interface MenuTreeNode {
      id: unknown;
      parentId: unknown;
      label: string;
      value: string | number;
      children: MenuTreeNode[];
    }

    const items = sourceMenus
      .filter((item) => item.id !== excludeId)
      .map((item) => ({
        id: item.id,
        parentId: item.parentId,
        label: item.menuName,
        value: item.id as string | number
      }));

    const itemMap = new Map<unknown, MenuTreeNode>(
      items.map((item) => [item.id, { ...item, children: [] as MenuTreeNode[] }])
    );
    const roots: MenuTreeNode[] = [];

    itemMap.forEach((item) => {
      if (item.parentId && itemMap.has(item.parentId)) {
        itemMap.get(item.parentId)?.children.push(item);
      } else {
        roots.push(item);
      }
    });

    return roots;
  }

  function flattenTreeOptions(
    tree: { label: string; value: unknown; children?: unknown[] }[],
    result: { label: string; value: unknown }[] = []
  ) {
    for (const node of tree || []) {
      result.push({ label: node.label, value: node.value });
      flattenTreeOptions(node.children as typeof tree, result);
    }
    return result;
  }

  function ensurePermissionModuleGroup(
    groupMap: Map<unknown, PermissionModuleGroup>,
    moduleId: unknown,
    moduleName: string
  ): PermissionModuleGroup {
    if (!groupMap.has(moduleId)) {
      groupMap.set(moduleId, {
        moduleId,
        moduleName,
        moduleSortOrder: Number.MAX_SAFE_INTEGER,
        menuMap: new Map<unknown, PermissionMenuNode>(),
        rootMenus: [],
        unassignedPermissions: []
      });
    }
    return groupMap.get(moduleId)!;
  }

  function sortPermissionMenuNodes(nodes: PermissionMenuNode[]): PermissionMenuNode[] {
    return (nodes || [])
      .map((node) => ({
        ...node,
        permissions: [...node.permissions].sort(comparePermissionByName),
        children: sortPermissionMenuNodes(node.children)
      }))
      .filter((node) => node.permissions.length > 0 || node.children.length > 0)
      .sort((a, b) => a.sortOrder - b.sortOrder || `${a.menuName}`.localeCompare(`${b.menuName}`, "zh-Hans-CN"));
  }

  function comparePermissionByName(a: Row, b: Row): number {
    return `${a.permissionName}`.localeCompare(`${b.permissionName}`, "zh-Hans-CN");
  }

  return {
    activeSection,
    activeResourceTab,
    roleKeyword,
    selectedRole,
    grantedPermissionIds,
    permissionKeyword,
    grantSaving,
    roleDialogVisible,
    roleGrantDrawerVisible,
    editingRole,
    resourceDialogVisible,
    resourceDialogTitle,
    resourceDialogForm,
    resourceDialogFields,
    resourceDialogSubmitter,
    logDrawerVisible,
    logTitle,
    logLoadFn,
    rows,
    loading,
    total,
    pageNum,
    pageSize,
    modules,
    menus,
    permissions,
    apiBindings,
    moduleQuery,
    menuQuery,
    permissionQuery,
    apiBindingQuery,
    moduleLoading,
    menuLoading,
    permissionLoading,
    apiBindingLoading,
    moduleStatusUpdatingId,
    menuStatusUpdatingId,
    permissionStatusUpdatingId,
    apiBindingStatusUpdatingId,
    moduleFilters,
    menuFilters,
    permissionFilters,
    apiBindingFilters,
    moduleColumns,
    menuColumns,
    permissionColumns,
    apiBindingColumns,
    permissionGroups,
    initializePage,
    initializeRoleSection,
    initializeResourceSection,
    loadModules,
    loadMenus,
    loadPermissions,
    loadApiBindings,
    handleModuleQuery,
    handleMenuQuery,
    handlePermissionQuery,
    handleApiBindingQuery,
    handleRoleQuery,
    resetRoleQuery,
    handleRolePageChange,
    handleRoleSizeChange,
    handleRoleSelect,
    openRoleCreate,
    openRoleGrant,
    handleRoleAction,
    onRoleSaved,
    togglePermission,
    handlePermissionKeywordChange,
    saveRolePermissionBindings,
    updateModuleFilter,
    updateMenuFilter,
    updatePermissionFilter,
    updateApiBindingFilter,
    resetModuleFilters,
    resetMenuFilters,
    resetPermissionFilters,
    resetApiBindingFilters,
    openModuleCreate,
    handleModuleAction,
    openMenuCreate,
    handleMenuAction,
    openPermissionCreate,
    handlePermissionAction,
    openApiBindingCreate,
    handleApiBindingAction,
    handleModuleStatusChange,
    handleMenuStatusChange,
    handlePermissionStatusChange,
    handleApiBindingStatusChange,
    onResourceSaved,
    resolveMenuName,
    beforePermissionDelete
  };
}
