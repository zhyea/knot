-- ============================================================================
-- 2026-10-28 · 异步任务档案改 MVEL 表达式（kb_async_task_profiles）
--
-- 背景（robin 2026-10-28）：
--   「kb_async_task_profiles 中 task_id_path/status_path/result_path 需要用 mvel 来获取，
--     status_failed/status_running/status_succeeded 用 mvel 来判断。AsyncTaskJsonPath 的实现有些不妥」
--
--   原实现（AsyncTaskJsonPath，已从代码删除）是一套自研的「a.b[0].c」下钻语法，
--   能表达的事实只有「取某个节点」。真实档案需要的比这多：
--     - 终态判定本身不是「取值相等」而是「这个状态算不算成功」，路径语法天然表达不了，
--       于是只能另配三个逗号列表 + Java 侧字符串比较（状态语义一半在配置、一半在代码）；
--     - 「成功标志在 data.status、失败原因在 data.error.code」这类结构，需要一次读多处；
--     - 每加一种写法都要改一次 Java 并重新发版，而档案本来就是「新增上游只加一行」的定位。
--   改用 MVEL 后：三个 *_path 是取值表达式，三个 status_* 是布尔表达式，语义全部落在档案里。
--
-- 本次变更是**语义变更**，不只是列注释：
--   task_id_path / status_path / result_path   JSON 路径  →  MVEL 取值表达式
--   status_succeeded / status_failed / status_running  逗号分隔取值集合  →  MVEL 布尔表达式
--
-- ⚠ 不迁移的后果不是「查不到」，而是任务永远 RUNNING 直到超时：
--   旧值 `succeeded,failed` 在 MVEL 里被当成语句序列（编译能过），求值期抛
--   「unresolvable property or identifier: succeeded」。代码侧刻意**不做兼容**——
--   求值失败一律抛 IllegalStateException 并指向本脚本，绝不宽容成 false
--   （宽容 = 配置错被伪装成上游慢，是最难查的一类事故）。故必须先跑本脚本再启动新版本。
--
-- 忽略大小写的口径收窄（须知）：
--   旧实现在 Java 里做 trim + equalsIgnoreCase，即 `SUCCEEDED`、`Succeed` 都能命中 `succeed`。
--   新语义按表达式列出的**字形**精确匹配。迁移时把旧列表里的字形原样搬进列表，
--   因此「运维写过的那些写法」全部保留；但**未列出的变体不再自动兼容**。
--   需要宽匹配就写 `lower(status) == 'succeeded'`，这属于档案配置的自由度。
--
-- 幂等性：MODIFY COLUMN 重复执行安全；数据改写带 old-format 判据（不含 == / [ / 单引号），
--         已是表达式形式（含 body / contains / == / 字面量）的行不会被二次改写。
-- 执行顺序：先跑本脚本，再 package / 启动应用。
-- 影响面：仅 kb_async_task_profiles 的 6 列（注释 / 长度）+ 存量档案数据；不动任务实例表
--         （kb_async_tasks.poll_spec_json 是提交时快照，在途任务按旧快照跑完即可，
--          旧快照里的逗号列表仍会被旧代码解释——所以**升级前先把在途任务跑完或直接放手超时**）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 列语义与容量
--    status_* 从 128 放宽到 512：布尔表达式比取值列表长（多条件 OR / contains 列举），
--    128 在真实档案里很容易顶到边，写进去被静默截断会把表达式截成语法错。
-- ---------------------------------------------------------------------------
ALTER TABLE kb_async_task_profiles
  MODIFY COLUMN task_id_path VARCHAR(255) NOT NULL
    COMMENT '创建响应取上游任务 ID 的 MVEL 表达式（如 data.id；取不到回 null → 回退同步链路）',
  MODIFY COLUMN status_path VARCHAR(255) NOT NULL
    COMMENT '查询响应取任务状态的 MVEL 表达式（如 data[0].status）；取不到回 null → 按进行中处理',
  MODIFY COLUMN status_succeeded VARCHAR(512) NOT NULL
    COMMENT '成功判定 MVEL 布尔表达式，可用变量 status/body（如 [''succeed'',''SUCCEEDED''] contains status）',
  MODIFY COLUMN status_failed VARCHAR(512) NOT NULL
    COMMENT '失败判定 MVEL 布尔表达式，可用变量 status/body（如 status == ''failed''）',
  MODIFY COLUMN status_running VARCHAR(512) DEFAULT NULL
    COMMENT '进行中判定 MVEL 布尔表达式；为空表示「除成功/失败外一律视为非终态」',
  MODIFY COLUMN result_path VARCHAR(255) DEFAULT NULL
    COMMENT '终态产物 URL 的 MVEL 表达式（如 data[0].outputs[0].url）；取不到回 null';

-- ---------------------------------------------------------------------------
-- 2) 存量档案数据：逗号列表 → MVEL 布尔表达式
--
--    转换公式（不拆分取值，故值个数不限、不会截断）：
--      succeed,SUCCEEDED
--        → 去掉逗号旁空格 : succeed,SUCCEEDED
--        → 逗号变 ''','''  : succeed''','''SUCCEEDED
--        → 两端加引号     : 'succeed','SUCCEEDED'
--        → 包成列表并加判定: ['succeed','SUCCEEDED'] contains status
--
--    old-format 判据（三条全满足才算旧值）：不含单引号、不含方括号、不含 ==。
--    任何表达式形式（contains / == / 字面量比较 / body.xxx）都至少命中一条，故不会二次改写。
-- ---------------------------------------------------------------------------
UPDATE kb_async_task_profiles
SET status_succeeded = CONCAT(
      '[', '''',
      REPLACE(REPLACE(REPLACE(TRIM(status_succeeded), ', ', ','), ' ,', ','), ',', ''','''),
      '''] contains status')
WHERE status_succeeded NOT LIKE '%''%'
  AND status_succeeded NOT LIKE '%[%'
  AND status_succeeded NOT LIKE '%==%'
  AND TRIM(status_succeeded) <> '';

UPDATE kb_async_task_profiles
SET status_failed = CONCAT(
      '[', '''',
      REPLACE(REPLACE(REPLACE(TRIM(status_failed), ', ', ','), ' ,', ','), ',', ''','''),
      '''] contains status')
WHERE status_failed NOT LIKE '%''%'
  AND status_failed NOT LIKE '%[%'
  AND status_failed NOT LIKE '%==%'
  AND TRIM(status_failed) <> '';

-- status_running 为空是合法语义（除成功/失败外一律非终态），故此处只改有值的行
UPDATE kb_async_task_profiles
SET status_running = CONCAT(
      '[', '''',
      REPLACE(REPLACE(REPLACE(TRIM(status_running), ', ', ','), ' ,', ','), ',', ''','''),
      '''] contains status')
WHERE status_running IS NOT NULL
  AND TRIM(status_running) <> ''
  AND status_running NOT LIKE '%''%'
  AND status_running NOT LIKE '%[%'
  AND status_running NOT LIKE '%==%';

-- ---------------------------------------------------------------------------
-- 3) 迁移后自检（三项都应为 0 / 空）
-- ---------------------------------------------------------------------------

-- CHECK-1 仍存在旧格式（不含单引号/方括号/==）的判定列：必须为 0
SELECT 'CHECK-1 未迁移的判定列' AS check_item,
       SUM(CASE WHEN NOT (status_succeeded LIKE '%''%' OR status_succeeded LIKE '%[%'
                          OR status_succeeded LIKE '%==%') THEN 1 ELSE 0 END) AS succeeded_legacy,
       SUM(CASE WHEN NOT (status_failed LIKE '%''%' OR status_failed LIKE '%[%'
                          OR status_failed LIKE '%==%') THEN 1 ELSE 0 END) AS failed_legacy,
       SUM(CASE WHEN status_running IS NOT NULL AND TRIM(status_running) <> ''
                     AND NOT (status_running LIKE '%''%' OR status_running LIKE '%[%'
                              OR status_running LIKE '%==%') THEN 1 ELSE 0 END) AS running_legacy
FROM kb_async_task_profiles;

-- CHECK-2 判定列里出现空字面量 `''`：必须为 0。
--         旧值带尾逗号（`succeed,`）或多写了逗号（`succeed,,failed`）时会产生空项，
--         空字面量在 MVEL 里是合法字符串，不会报错，只会让判定诡异——所以必须人工清掉。
--         ⚠ 不要用「有没有空格」当判据：`] contains status` 本身就带空格。
SELECT 'CHECK-2 空字面量' AS check_item, id, profile_code,
       status_succeeded, status_failed, status_running
FROM kb_async_task_profiles
WHERE status_succeeded LIKE '%''''%'
   OR status_failed LIKE '%''''%'
   OR status_running LIKE '%''''%';

-- CHECK-3 迁移后的档案全貌（逐行人工过一眼：表达式读起来是否就是当初想的那个意思）
SELECT 'CHECK-3 档案表达式全貌' AS check_item,
       id, profile_code, protocol, query_path,
       task_id_path, status_path,
       status_succeeded, status_failed, status_running, result_path
FROM kb_async_task_profiles
ORDER BY id;
