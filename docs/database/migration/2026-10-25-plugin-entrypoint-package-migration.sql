-- 插件表二次收口：entrypoint 包前缀改写 + 去重 + 删六列 + 列宽归一 + 加 entrypoint 唯一键
--
-- 背景（2026-10-25 包结构迁移后收口）：`kb_plugin_instances` 重新定位为
-- 「PluginDescriptor 的物化 + 运维开关」，插件定义唯一来源改到代码侧描述符，
-- data.sql 不再预置插件种子（种子 + INSERT IGNORE 正是 entrypoint 漂移的根源）。
--
-- 本脚本合并了两件事，都必须在**启动新 jar 之前**跑完：
--
-- 【一】必要的数据修正（不跑就起不来 / 起来后整屏异常）
--   a) 插件实现类换过包：org.chobit.knot.gateway.plugin.* → org.chobit.knot.plugin.*。
--      data.sql 的值即使改了也进不来（那段曾是 INSERT IGNORE + 显式 id，既有库主键冲突静默跳过），
--      于是 reconcile 按 entrypoint 匹配全部落空 → 8 个插件判成「代码新增」→ 重复插入 + 整屏 orphaned。
--   b) 误启过新 jar 的库里，同一 entrypoint 会有两行（种子行 + reconcile 自动插入行），需去重。
--   c) instance_code 需归一回 64：旧版存在「派生码 65 字符 > 列宽」的缺陷，临时加宽到 128 止血；
--      现在 code 由描述符显式声明并在启动期校验长度，列宽回到契约值 64。
--
-- 【二】结构清理（前后端零读取方，已核）
--   删除六列：plugin_code / plugin_name / capability_name / source_type / config_json / concurrency_limit。
--   其中 plugin_code / plugin_name 是「插件包」分组概念，已被单表收口压成
--   「能力族 → 插件实例」两级；capability_name 是能力码的中文副本；source_type 的 JAR 分支
--   已确认不支持；config_json 与 concurrency_limit 从未写入也从未读取。
--   新增 uk_plugin_instances_entrypoint，使启动期 reconcile 能用 ON DUPLICATE KEY UPDATE 原子 upsert。
--
-- 幂等性：UPDATE 以旧前缀为条件、DELETE 以「存在同 entrypoint 的更小 id 行」为条件、
-- MODIFY / ADD KEY 重复执行无副作用。⚠ 但 DROP COLUMN 不幂等（MySQL 8 无 IF EXISTS，
-- 重复执行报 1091），脚本按「一次性升级」设计，重复跑请手工跳过第 4 步。
--
-- 执行顺序：既有库「先跑本脚本，再启新 jar」。

-- ── 1. entrypoint 包前缀改写 ──────────────────────────────────────────────
UPDATE kb_plugin_instances
SET entrypoint = REPLACE(entrypoint, 'org.chobit.knot.gateway.plugin.', 'org.chobit.knot.plugin.')
WHERE entrypoint LIKE 'org.chobit.knot.gateway.plugin.%';

-- ── 2. 按 entrypoint 去重（只对「已经误启过新 jar」的库有实际动作）──────────
-- 同一 entrypoint 保留 id 最小者：那是 data.sql 预置的种子行，instance_code 是人类可读短码，
-- 且带着运维改过的 status / instance_name；被删的是 reconcile 自动插入的长码重复行。
DELETE t
FROM kb_plugin_instances t
JOIN kb_plugin_instances keep
  ON keep.entrypoint = t.entrypoint
 AND keep.id < t.id;

-- ── 3. instance_code 归一回 64 ──────────────────────────────────────────
-- 必须在第 2 步之后：去重后留下的都是短码（最长 29），ALTER 不会因超长失败。
ALTER TABLE kb_plugin_instances
  MODIFY COLUMN instance_code VARCHAR(64) NOT NULL COMMENT '实例业务码（PluginDescriptor.code；启动期 reconcile 校验不超此列宽）';

-- ── 4. 删列（先删依赖列上的索引，避免 MySQL 处理复合索引残留）──────────────
ALTER TABLE kb_plugin_instances
  DROP INDEX idx_plugin_instances_package_status,
  DROP INDEX idx_plugin_instances_capability_status;

ALTER TABLE kb_plugin_instances
  DROP COLUMN plugin_code,
  DROP COLUMN plugin_name,
  DROP COLUMN capability_name,
  DROP COLUMN source_type,
  DROP COLUMN config_json,
  DROP COLUMN concurrency_limit;

-- ── 5. entrypoint 唯一键（upsert 的前提）─────────────────────────────────
-- VARCHAR(255) utf8mb4 = 1020 字节 < InnoDB DYNAMIC 行格式的 3072 字节索引上限。
-- 若此处报 Duplicate entry，说明第 2 步没覆盖到（手工复查 entrypoint 重复行）。
ALTER TABLE kb_plugin_instances
  ADD UNIQUE KEY uk_plugin_instances_entrypoint (entrypoint);

-- ── 验证（人工执行，不在迁移流程内）─────────────────────────────────────
-- 期望：rows_total = 8，old_prefix_left = 0，duplicate_entrypoints = 0，max_code_len <= 64
-- SELECT
--   (SELECT COUNT(*) FROM kb_plugin_instances)                                      AS rows_total,
--   SUM(entrypoint LIKE 'org.chobit.knot.gateway.plugin.%')                         AS old_prefix_left,
--   (SELECT COUNT(*) FROM (SELECT entrypoint FROM kb_plugin_instances
--                           GROUP BY entrypoint HAVING COUNT(*) > 1) d)             AS duplicate_entrypoints,
--   (SELECT MAX(CHAR_LENGTH(instance_code)) FROM kb_plugin_instances)               AS max_code_len
-- FROM kb_plugin_instances;
