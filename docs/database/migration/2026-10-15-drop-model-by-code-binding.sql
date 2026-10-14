-- =====================================================================
-- 撤销「供应商模型按 code 取详情」接口授权绑定（2026-10-15）
-- ---------------------------------------------------------------------
-- 背景：id=207（GET /api/models/code/{code}，permission 84）由 2026-10-14
--       迁移引入，唯一用途是让前端路由规则测试抽屉按模型池条目的 modelCode
--       解析协议（修巡检问题 #1）。
--       2026-10-15 落地 #3 后，协议改由后端 GET /api/routing-rules/{id}/debug-protocols
--       权威下发，该端点与前端 api/models.ts#getModelByCode 均已零调用被删除，
--       绑定与端点一并撤销，避免遗留「有授权无端点」的悬空配置。
-- 幂等：按主键 id 删除，重复执行无副作用（第二次影响行数为 0）。
-- 执行顺序：先跑本迁移（或直接随应用重启前执行），再启动应用。
-- =====================================================================

-- 段一：撤销 API 绑定
DELETE FROM ks_api_permission_bindings WHERE id = 207;

-- =====================================================================
-- 复核（执行后应全部符合预期）
-- =====================================================================
-- select count(*) from ks_api_permission_bindings where id = 207;              -- 0
-- select count(*) from ks_api_permission_bindings
--   where path_pattern = '/api/models/code/{code}';                            -- 0
-- select count(*) from ks_api_permission_bindings
--   where path_pattern = '/api/routing-rules/{id}/debug-protocols';            -- 1（新接口仍在，不可误删）
