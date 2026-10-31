-- =============================================================================
-- 2026-10-31 视频分辨率档位：新建 DB 枚举分类 + 修正内置预设的分辨率写法
--
-- 两件事，同一批变更：
--   §1 新建枚举分类 `video_resolution`（供计费配置界面的档位名下拉建议）
--   §2 把内置调试预设里的 `"size":"1280x720"` 改成 `"size":"720p"`
--
-- ── §1 为什么要新建分类 ─────────────────────────────────────────────────────
-- 档位清单原来在 Java 枚举 `ResolutionTierEnum` 里，厂商加一档就得改代码发版。
-- 2026-10-31 拍板：清单进 DB 枚举，运营在「枚举配置」界面自己加，不用发版。
--
-- ⚠ 清单**只供前端下拉建议，网关不拿它做匹配**。归一逻辑是「去首尾空白 + 转大写」，
--    没有任何别名推断：配置了 480P 就只认 480p / 480P，`480` / `854x480` 都不等价。
--    remark 里写的历史写法只给人看，代码不读——刻意如此，别「顺手」把它接进匹配。
--
-- ── §2 为什么要改内置预设 ───────────────────────────────────────────────────
-- `builtin-video-generations-*` 两条预设是网关自带的调试样例，`size` 正是视频计费的
-- 分档键。归一收紧后 `1280x720` 会归一出 `1280X720`，与规则里配的 `720P` 不匹配，
-- 严格取价会直接报错——自带示例点开就失败，所以一并改成档位名 `720p`。
--
-- 幂等：§1 靠唯一键 `(category_id, item_code)` + INSERT IGNORE；
--       §2 靠 `LIKE '%"size":"1280x720"%'` + REPLACE，重复执行无副作用。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §1 建分类（按唯一键 category 判断，不写死 id——存量库的 AUTO_INCREMENT 不可预知）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_enum_categories (category, category_name, is_system, is_enabled)
VALUES ('video_resolution', '视频分辨率档位', 0, 1);

-- ---------------------------------------------------------------------------
-- §1 建档位项：7 个标准档位。category_id 通过 category 反查，避免写死 id
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled, remark)
SELECT c.id, v.item_code, v.item_label, v.sort_order, 1, v.remark
FROM ks_enum_categories c
         CROSS JOIN (SELECT '480P' AS item_code, '480P（854×480）' AS item_label, 1 AS sort_order,
                            '其他常见写法：480 / 480p / 854x480（仅备注，网关不做别名推断）' AS remark
                     UNION ALL
                     SELECT '540P', '540P（960×540）', 2,
                            '其他常见写法：540 / 540p / 960x540（仅备注，网关不做别名推断）'
                     UNION ALL
                     SELECT '720P', '720P（1280×720）', 3,
                            '其他常见写法：720 / 720p / 1280x720（仅备注，网关不做别名推断）'
                     UNION ALL
                     SELECT '768P', '768P（1366×768）', 4,
                            '其他常见写法：768 / 768p / 1366x768 / 1024x768（仅备注，网关不做别名推断）'
                     UNION ALL
                     SELECT '1080P', '1080P（1920×1080）', 5,
                            '其他常见写法：1080 / 1080p / 1920x1080（仅备注，网关不做别名推断）'
                     UNION ALL
                     SELECT '2K', '2K（2560×1440）', 6,
                            '其他常见写法：1440 / 1440p / 2k / qhd / 2560x1440（仅备注，网关不做别名推断）'
                     UNION ALL
                     SELECT '4K', '4K（3840×2160）', 7,
                            '其他常见写法：2160 / 2160p / 4k / uhd / 3840x2160（仅备注，网关不做别名推断）') v
WHERE c.category = 'video_resolution';

-- ---------------------------------------------------------------------------
-- §2 修正内置视频预设：1280x720 → 720p
--
-- 只改仍停留在旧写法的那两条；管理员改过的预设（request_body 已不含该串）不会被碰。
-- ---------------------------------------------------------------------------
UPDATE kb_test_request_presets
SET request_body = REPLACE(request_body, '"size":"1280x720"', '"size":"720p"')
WHERE code IN ('builtin-video-generations-basic', 'builtin-video-generations-kling')
  AND request_body LIKE '%"size":"1280x720"%';

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------

-- 1) 分类应返回 1 行，is_system 必须是 0（运营要能增删档位）
SELECT c.category, c.category_name, c.is_system, c.is_enabled
FROM ks_enum_categories c
WHERE c.category = 'video_resolution';

-- 2) 档位项应返回 7 行，按 sort_order 排列
SELECT cfg.item_code, cfg.item_label, cfg.sort_order, cfg.is_enabled
FROM ks_enum_configs cfg
         JOIN ks_enum_categories c ON c.id = cfg.category_id
WHERE c.category = 'video_resolution'
ORDER BY cfg.sort_order;

-- 3) 内置视频预设不应再有 1280x720（返回 0 行即为成功）
SELECT code, request_body
FROM kb_test_request_presets
WHERE code IN ('builtin-video-generations-basic', 'builtin-video-generations-kling')
  AND request_body LIKE '%"size":"1280x720"%';
