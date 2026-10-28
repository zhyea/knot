-- ============================================================================
-- 2026-10-27 · 通用异步任务表（kb_async_tasks / kb_async_task_profiles）+ 绑定引用列
--
-- 背景（robin 2026-10-27）：
--   1) 一批上游（可灵 Kling、MiniMax H3、Sora、Runway、Luma…）的媒体生成是「提交任务 + 轮询」
--      异步模型：create 只回 task_id，用量与产物只在终态 query 响应里。
--      网关当前是同步计费链路（一次请求一个响应体立刻提取），接到这类上游会把提交回执
--      当成终态，被「至少一秒」兜底成 1 秒计费（见 docs/视频计费规则不足盘点-2026-10-27.md A2）。
--   2) 早期方案（docs/视频异步任务轮询拉取技术方案-2026-10-26.md）把待轮询任务放在
--      Redis / Caffeine：Redis 不可用即 503（fail-hard），任务随进程或缓存丢失即永久漏计。
--      robin 拍板改「加一个异步任务表」——**DB 为唯一真相源**，可查、可恢复、可归档。
--   3) 表必须**适配各种异步任务，不局限于视频**：故不建 async_video_tasks，
--      而是「任务实例表 + 查询口径档案表」两层——前者只管一次任务的生命周期，
--      后者描述「怎么查上游」，二者以 profile_code 关联，新增一家异步上游 = 加一行档案。
--
-- 设计要点（详见 docs/异步任务表与通用轮询方案-2026-10-27.md）：
--   - 任务类型用 `protocol`（ModelApiProtocolEnum）表达，不另设 task_type：
--     同一件事写两遍必然漂移，protocol 已是「这次调用是什么能力」的唯一事实。
--   - `poll_spec_json` 是**提交时的口径快照**：档案改了不影响在途任务的轮询与计费，
--     保证「同一笔任务无论何时完成，算法与口径可复现」。
--   - 多节点靠 DB 原子 claim（claim_id + claim_expires_at），不引入分布式锁中间件；
--     节点宕机后占位自动过期，无需显式释放。
--
-- 影响面：新增两张表 + kb_model_api_bindings 加一列（可空，存量行语义不变 = 同步链路）。
--         不改任何既有列，不改 Kafka 流量消息协议。
-- 执行顺序约定：先跑本脚本，再 package / 启动应用（代码与迁移同版本上线，不留兼容分支）。
-- 幂等性：建表用 IF NOT EXISTS；加列用 information_schema 判存在，重复执行安全。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 异步任务档案（「怎么查上游」的模板，按档案编码复用）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_async_task_profiles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  profile_code VARCHAR(64) NOT NULL COMMENT '异步任务档案编码（绑定引用，非主键 id）',
  protocol VARCHAR(64) NOT NULL COMMENT '所属 API 协议编码（ModelApiProtocolEnum），用于绑定一致性校验',
  query_method VARCHAR(8) NOT NULL DEFAULT 'GET' COMMENT '查询方法',
  query_path VARCHAR(255) NOT NULL COMMENT '查询路径模板；{task_id}/{external_task_id} 可出现在 path 段或 query 段（如 /tasks?task_ids={task_id}）',
  task_id_path VARCHAR(255) NOT NULL COMMENT '创建响应中取上游任务 ID 的 JSON 路径（如 data.id）',
  external_task_field VARCHAR(128) DEFAULT NULL COMMENT '创建请求体中填自定义任务 ID 的字段路径（如 options.external_task_id）；为空表示上游不支持',
  status_path VARCHAR(255) NOT NULL COMMENT '查询响应中取任务状态的 JSON 路径（如 data[0].status）',
  status_succeeded VARCHAR(128) NOT NULL COMMENT '终态成功取值，逗号分隔多值',
  status_failed VARCHAR(128) NOT NULL COMMENT '终态失败取值，逗号分隔多值',
  status_running VARCHAR(128) DEFAULT NULL COMMENT '非终态取值；为空表示「除成功/失败外一律视为非终态」',
  result_path VARCHAR(255) DEFAULT NULL COMMENT '终态产物 URL 的 JSON 路径（如 data[0].outputs[0].url）',
  result_expires_days INT DEFAULT NULL COMMENT '上游产物有效期（天），用于 result_expires_at 与客户端提示',
  poll_interval_ms INT NOT NULL DEFAULT 5000 COMMENT '首次轮询后的基准间隔',
  poll_backoff_base_ms INT NOT NULL DEFAULT 1000 COMMENT '退避基数，与 BackoffCalculator 同口径',
  poll_backoff_max_ms INT NOT NULL DEFAULT 30000 COMMENT '退避上限',
  max_attempts INT NOT NULL DEFAULT 360 COMMENT '轮询次数上限，与 timeout_ms 先到先生效',
  timeout_ms BIGINT NOT NULL DEFAULT 1800000 COMMENT 'knot 侧任务超时（默认 30min），到期转 EXPIRED',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_async_task_profiles_code (profile_code),
  KEY idx_async_task_profiles_protocol (protocol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步任务查询口径档案';

-- ---------------------------------------------------------------------------
-- 2) 异步任务实例（一次提交 = 一行；DB 为唯一真相源）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_async_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL COMMENT '对外任务句柄（客户端轮询用，非主键 id）',
  profile_code VARCHAR(64) NOT NULL COMMENT '异步任务档案编码（当时口径见 poll_spec_json）',
  protocol VARCHAR(64) NOT NULL COMMENT 'API 协议编码，决定结果解析与计费分派',
  upstream_task_id VARCHAR(128) DEFAULT NULL COMMENT '上游任务 ID',
  external_task_id VARCHAR(128) DEFAULT NULL COMMENT '自定义任务 ID；上游支持时填 knot 句柄，供反查恢复',
  status VARCHAR(24) NOT NULL COMMENT '任务状态（AsyncTaskStatusEnum）：PENDING-待首次轮询 POLLING-轮询中 SUCCEEDED-成功 FAILED-失败 EXPIRED-超时 CANCELLED-已取消',
  attempts INT NOT NULL DEFAULT 0 COMMENT '已轮询次数',
  next_poll_at DATETIME(3) DEFAULT NULL COMMENT '下次轮询时刻；NULL 表示终态不再轮询',
  claim_id VARCHAR(64) DEFAULT NULL COMMENT '轮询占位标识（nodeId-uuid），防多节点重复处理',
  claim_expires_at DATETIME(3) DEFAULT NULL COMMENT '占位过期时刻；节点宕机后自动可回收，无需显式释放',
  last_polled_at DATETIME(3) DEFAULT NULL COMMENT '上次实际发起查询的时刻',
  request_json JSON NOT NULL COMMENT '原始请求体：终态提取计费属性的唯一输入（提取器只读这一份）',
  poll_spec_json JSON NOT NULL COMMENT '提交时快照的查询口径（路径 / 终态取值 / 轮询参数），档案变更不影响在途任务',
  context_json JSON NOT NULL COMMENT '重建计费上下文：路由快照 / 命中快照 / 追踪信息（计费规则引用不在此处，见 billing_rule_code）',
  result_json JSON DEFAULT NULL COMMENT '终态产物与用量摘要',
  error_code VARCHAR(64) DEFAULT NULL COMMENT '失败/超时错误码',
  error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败/超时原因（上游 message 原文）',
  billing_rule_code VARCHAR(64) DEFAULT NULL COMMENT '计费规则业务码（计费复现用；不与 context_json 重复存放）',
  billing_rule_version_code VARCHAR(64) DEFAULT NULL COMMENT '计费规则版本编码，保证金额可复现',
  submitted_at DATETIME(3) NOT NULL COMMENT '上游创建成功时刻',
  completed_at DATETIME(3) DEFAULT NULL COMMENT '终态时刻',
  expires_at DATETIME(3) NOT NULL COMMENT 'knot 侧任务超时时刻，到期转 EXPIRED',
  result_expires_at DATETIME(3) DEFAULT NULL COMMENT '上游产物失效时刻（由 result_expires_days 推算）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_async_tasks_code (task_code),
  KEY idx_async_tasks_due (status, next_poll_at),
  KEY idx_async_tasks_claim (claim_id),
  KEY idx_async_tasks_upstream (upstream_task_id),
  KEY idx_async_tasks_external (external_task_id),
  KEY idx_async_tasks_profile (profile_code),
  KEY idx_async_tasks_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步任务实例（提交 + 轮询拉取）';

-- ---------------------------------------------------------------------------
-- 3) 模型绑定引用异步档案（为空 = 同步链路，存量行语义不变）
--    ⭐ 异步与否是「绑定」的属性，不是协议的属性：同为 VIDEO_GENERATIONS，
--       MiniMax H3 异步、其它模型可能同步，故不能只按 protocol 判。
-- ---------------------------------------------------------------------------
SET @has_async_profile = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_model_api_bindings'
    AND COLUMN_NAME  = 'async_profile_code'
);

SET @sql_add_async_profile = IF(@has_async_profile = 0,
  "ALTER TABLE kb_model_api_bindings
     ADD COLUMN async_profile_code VARCHAR(64) DEFAULT NULL
     COMMENT '异步任务档案编码（kb_async_task_profiles.profile_code）；为空表示同步链路'
     AFTER stream_usage_extractor",
  "SELECT 'kb_model_api_bindings.async_profile_code 已存在，跳过' AS skipped");
PREPARE stmt_add_async_profile FROM @sql_add_async_profile;
EXECUTE stmt_add_async_profile;
DEALLOCATE PREPARE stmt_add_async_profile;

-- ---------------------------------------------------------------------------
-- 4) 迁移后自检（应全部为空 / 符合预期）
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 新表是否建立' AS check_item,
       (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks') AS async_tasks,
       (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_task_profiles') AS async_profiles;

SELECT 'CHECK-2 绑定列是否建立' AS check_item,
       (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'kb_model_api_bindings'
           AND COLUMN_NAME  = 'async_profile_code') AS async_profile_column;
