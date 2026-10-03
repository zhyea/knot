-- 回滚：恢复 5 个零消费孤儿 DB 枚举分类（2026-10-03）
-- 对应迁移：2026-10-03-retire-orphan-enums.sql
--
-- 仅在需要回退「删除孤儿枚举分类」时使用。这些分类本就零业务消费，
-- 恢复它们**不会改变任何运行时行为**，只是把误配置入口放回枚举管理页。
-- 若恢复后要真正启用其中某个分类，仍需另行开发消费方（DB 有值 ≠ 代码会读）。

INSERT IGNORE INTO ks_enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(11, 'plugin_source_type',   '插件来源类型',   0, 1),
(13, 'alert_level',          '告警级别',       1, 1),
(14, 'risk_level',           '风险级别',       1, 1),
(26, 'plugin_fail_mode',     '插件失败策略',   0, 1),
(27, 'plugin_result_status', '插件结果状态',   0, 1);

INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(11, 'BUILTIN',          '内置',     1, 1),
(11, 'LOCAL_JAR',        '本地 JAR', 2, 1),
(11, 'REMOTE_REGISTRY',  '远程仓库', 3, 1),
(13, 'CRITICAL', '严重', 1, 1),
(13, 'HIGH',     '高',   2, 1),
(13, 'MEDIUM',   '中',   3, 1),
(13, 'LOW',      '低',   4, 1),
(14, 'HIGH',   '高', 1, 1),
(14, 'MEDIUM', '中', 2, 1),
(14, 'LOW',    '低', 3, 1),
(26, 'FAIL_OPEN',  '失败放行', 1, 1),
(26, 'FAIL_CLOSE', '失败阻断', 2, 1),
(27, 'SUCCESS',      '成功', 1, 1),
(27, 'SKIPPED',      '跳过', 2, 1),
(27, 'FAILED',       '失败', 3, 1),
(27, 'TIMEOUT',      '超时', 4, 1),
(27, 'OPEN_CIRCUIT', '熔断', 5, 1);

-- 回滚后核对（应恢复 5 个分类、17 条 configs）
-- SELECT COUNT(*) FROM ks_enum_categories WHERE id IN (11,13,14,26,27);   -- 5
-- SELECT COUNT(*) FROM ks_enum_configs    WHERE category_id IN (11,13,14,26,27);  -- 17
