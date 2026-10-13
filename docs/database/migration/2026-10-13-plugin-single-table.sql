-- =====================================================================
-- 插件管理单表收口迁移（2026-10-13）
-- ---------------------------------------------------------------------
-- 背景：kb_plugin_instances 单表收口，包/能力/绑定三表删除，其展示信息
--       （plugin_name/version/source_type/capability_*/extension_point/
--       stage_code/order_no/entrypoint）反范式冗余进 instance；新增
--       is_deleted（逻辑删除）+ orphaned（代码已移除标记）。scope 维度
--       按决策永久移除；status 改为 EnabledStatusEnum（1/0）。
-- 幂等：每段均先判列/表是否存在，可重复执行不报错。
-- 执行顺序：先跑本迁移，再启动应用（PluginReconcileRunner 按 entrypoint 校正）。
-- =====================================================================

SET @db = DATABASE();

-- =====================================================================
-- 段一：kb_plugin_instances 加列 + 回填 + 删 capability_id
-- =====================================================================

-- ---- 加列（已存在则跳过） ----
SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='plugin_name');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN plugin_name VARCHAR(100) DEFAULT NULL COMMENT '插件名称（反范式冗余，原 packages.plugin_name）'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='entrypoint');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN entrypoint VARCHAR(255) NOT NULL DEFAULT '' COMMENT '插件实现类全限定名（IOC 稳定键）'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='source_type');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'BUILTIN' COMMENT '来源类型（BUILTIN/JAR，当前仅 BUILTIN）'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='capability_code');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN capability_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '能力 code（反范式冗余，原 capabilities.capability_code）'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='capability_name');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN capability_name VARCHAR(100) DEFAULT NULL COMMENT '能力名称（反范式冗余，原 capabilities.capability_name）'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='extension_point');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN extension_point VARCHAR(64) NOT NULL DEFAULT '' COMMENT '扩展点（反范式冗余，原 capabilities.extension_point）'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='stage_code');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN stage_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '执行阶段（反范式冗余，原 capabilities.stage_code / bindings.stage_code）'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='order_no');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN order_no INT NOT NULL DEFAULT 100 COMMENT '同阶段执行顺序（反范式冗余，原 bindings.order_no）'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='is_deleted');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（死行不展示）：1-已删除 0-未删除'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='orphaned');
SET @sql = IF(@has=0, "ALTER TABLE kb_plugin_instances ADD COLUMN orphaned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '代码已移除标记（reconcile 检出 IOC 无此插件）：1-孤儿 0-正常'", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 回填展示/结构列（仅存量 capability_id 非空的行） ----
UPDATE kb_plugin_instances pi
  JOIN kb_plugin_capabilities pc ON pc.id = pi.capability_id
  JOIN kb_plugin_packages pp ON pp.plugin_code = pc.plugin_code
  LEFT JOIN kb_plugin_bindings pb ON pb.instance_code = pi.instance_code
SET
  pi.plugin_name     = pp.plugin_name,
  pi.version         = pp.version,
  pi.source_type     = pp.source_type,
  pi.capability_code = pc.capability_code,
  pi.capability_name = pc.capability_name,
  pi.extension_point = pc.extension_point,
  pi.stage_code      = pc.stage_code,
  pi.order_no        = COALESCE(pb.order_no, 100),
  pi.is_deleted      = 0,
  pi.orphaned        = 0
WHERE pi.capability_id IS NOT NULL;

-- ---- entrypoint 回填：6 个已知 SYNC 日志插件按 instance_code 精确映射类 FQN ----
UPDATE kb_plugin_instances SET entrypoint = 'org.chobit.knot.gateway.plugin.builtin.GatewayRequestLoggingPlugin'  WHERE instance_code = 'gateway-request-log';
UPDATE kb_plugin_instances SET entrypoint = 'org.chobit.knot.gateway.plugin.builtin.GatewayResponseLoggingPlugin' WHERE instance_code = 'gateway-response-log';
UPDATE kb_plugin_instances SET entrypoint = 'org.chobit.knot.gateway.plugin.builtin.GatewayErrorLoggingPlugin'    WHERE instance_code = 'gateway-error-log';
UPDATE kb_plugin_instances SET entrypoint = 'org.chobit.knot.gateway.plugin.builtin.UpstreamRequestLoggingPlugin' WHERE instance_code = 'provider-request-log';
UPDATE kb_plugin_instances SET entrypoint = 'org.chobit.knot.gateway.plugin.builtin.UpstreamResponseLoggingPlugin' WHERE instance_code = 'provider-response-log';
UPDATE kb_plugin_instances SET entrypoint = 'org.chobit.knot.gateway.plugin.builtin.UpstreamErrorLoggingPlugin'    WHERE instance_code = 'provider-error-log';
-- 其余存量行：兜底用 packages.entrypoint（可能仅是包级主类，启动期 reconcile 会校正）
UPDATE kb_plugin_instances pi
  JOIN kb_plugin_packages pp ON pp.plugin_code = pi.plugin_code
SET pi.entrypoint = COALESCE(NULLIF(pp.entrypoint, ''), pi.instance_code)
WHERE pi.entrypoint = '' OR pi.entrypoint IS NULL;

-- ---- 删除 capability_id 列（已存在则跳过） ----
SET @has = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_instances' AND COLUMN_NAME='capability_id');
SET @sql = IF(@has>0, 'ALTER TABLE kb_plugin_instances DROP COLUMN capability_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 段二：删除三表（已删除则跳过）
-- =====================================================================
SET @has = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_bindings');
SET @sql = IF(@has>0, 'DROP TABLE kb_plugin_bindings', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_capabilities');
SET @sql = IF(@has>0, 'DROP TABLE kb_plugin_capabilities', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has = (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=@db AND TABLE_NAME='kb_plugin_packages');
SET @sql = IF(@has>0, 'DROP TABLE kb_plugin_packages', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
