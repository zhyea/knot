-- 模型池增加 sticky_policy 列（池内粘性路由策略，默认关闭）（2026-10-12）
--
-- 背景：模型池的 RANDOM / WEIGHTED 选择策略每请求重掷，同一会话的多轮请求会漂移到
--   池内不同供应商账户/模型，导致上游 prompt cache（按账户 + 区域 + 部署命中）全部落空。
--   粘性路由按请求头携带的粘性键做一致性 hash，把命中目标置顶为池内首选（其余保持
--   池策略原序作 failover），让同一会话稳定落到一个上游，从而命中 prompt cache。
--   粘性只在模型池内生效：路由规则的多个目标是主备语义（切换该由故障驱动），
--   池内模型才是均衡语义（池内模型全部映射同一统一模型，功能等价）。
--   方案为无状态一致性 hash（不记录绑定、无 TTL、无需缓存粘性结果），
--   hash 环本身用进程内 Caffeine 缓存，不引入共享存储。
--   schema.sql 已把该列纳入 CREATE TABLE 作为权威定义。
--   本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐列；全新库由 schema.sql 直接建出。
--
-- 幂等：先查 information_schema，列不存在才 ALTER；已存在则跳过。

SET @exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_model_pools'
      AND COLUMN_NAME = 'sticky_policy'
);
SELECT @exists AS sticky_policy_exists;

SET @sql = IF(
    @exists = 0,
    'ALTER TABLE kb_model_pools ADD COLUMN sticky_policy TEXT DEFAULT NULL COMMENT ''池内粘性路由策略（JSON）；空表示未启用，池选择走 selection_strategy'' AFTER selection_strategy',
    'SELECT 1' -- 列已存在，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验：补列后该列必须存在（宏为 NULL 时表示未加成功，需人工排查）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_model_pools'
  AND COLUMN_NAME = 'sticky_policy';
