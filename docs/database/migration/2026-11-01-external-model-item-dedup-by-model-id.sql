-- =============================================================================
-- 2026-11-01 外部模型条目去重键收窄：(source_code, model_id) → (model_id)
--
-- 配套拍板（robin，2026-11-01）：同步去重只认 model_id，同一模型跨源视为同一条目，
-- 后同步的源覆盖旧数据并把条目归属（source_code）翻到新源。
-- 代码侧：getItemBySourceKey → getItemByModelId；updateItem 增写 source_code。
--
-- ── 为什么要先清重复 ─────────────────────────────────────────────────────────
-- 唯一键从 (source_code, model_id) 收窄到 (model_id)，存量里若有跨源同 model_id
-- 的条目，ADD UNIQUE KEY 会直接失败。当前存量只有 OPENROUTER 一个源（XFUSIONAI
-- 2026-11-01 才接入且未同步过），理论上零重复；§1 是防御性的，无重复时删 0 行。
--
-- ⚠ 本脚本**非幂等**：ALTER TABLE 的 ADD UNIQUE KEY 无 IF NOT EXISTS，重复执行
--   会报 Duplicate key name——执行一次即止，报「索引不存在」说明已改过，勿重跑 §2。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §1 防御性清理：同 model_id 多条时保留 id 最大的一条（最近插入），其余删除
--    无重复时两条语句均影响 0 行，可安全执行。
-- ---------------------------------------------------------------------------

-- §1.1 重复组内转移 logical_model_id：保留条未绑定而被删条已绑定时，把绑定交给保留条
UPDATE kx_model_items keep
JOIN (
    SELECT g.model_id, MAX(g.logical_model_id) AS lmid
    FROM (SELECT model_id, logical_model_id FROM kx_model_items) g
    GROUP BY g.model_id
    HAVING COUNT(*) > 1 AND MAX(g.logical_model_id) IS NOT NULL
) dup ON dup.model_id = keep.model_id
SET keep.logical_model_id = dup.lmid
WHERE keep.logical_model_id IS NULL
  AND keep.id = (
      SELECT max_id FROM (
          SELECT model_id AS mid, MAX(id) AS max_id
          FROM (SELECT model_id, id FROM kx_model_items) t
          GROUP BY mid
      ) m WHERE m.mid = keep.model_id
  );

-- §1.2 删除重复条（每组留 id 最大的一条）
DELETE FROM kx_model_items
WHERE id NOT IN (
    SELECT max_id FROM (
        SELECT MAX(id) AS max_id FROM kx_model_items GROUP BY model_id
    ) t
);

-- ---------------------------------------------------------------------------
-- §2 收窄唯一键（一次性执行，见头部说明）
-- ---------------------------------------------------------------------------
ALTER TABLE kx_model_items
    DROP INDEX uk_external_model_item,
    ADD UNIQUE KEY uk_external_model_item (model_id);

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------

-- 1) 唯一键应只含 model_id 一列（返回 1 行、Column_name=model_id 即成功）
SHOW INDEX FROM kx_model_items WHERE Key_name = 'uk_external_model_item';

-- 2) 不应存在同 model_id 多条（返回 0 行即为成功）
SELECT model_id, COUNT(*) AS cnt
FROM kx_model_items
GROUP BY model_id
HAVING COUNT(*) > 1;
