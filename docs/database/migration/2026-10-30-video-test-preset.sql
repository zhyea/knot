-- =============================================================================
-- 2026-10-30 视频调试：补一条 Kling 文生视频预设请求
--
-- 为什么单独写脚本：`data.sql` 用的是 `INSERT IGNORE`，而 `kb_test_request_presets.code`
-- 是唯一键——存量库里 `builtin-video-generations-basic` 已经在，新加的这条也只是「另一个 code」，
-- 理论上会被 `INSERT IGNORE` 插进去。但**存量库不会重新执行 data.sql**（它只在空库初始化时跑），
-- 所以想让老环境也看到这条预设，只能另写迁移。这与「任务协议绑定方案 §6」同型。
--
-- 幂等：`INSERT ... ON DUPLICATE KEY UPDATE remark = remark`（命中唯一键即无操作），
-- 重复执行不会覆盖管理员改过的 name / request_body。
-- =============================================================================

INSERT INTO kb_test_request_presets (code, name, protocol_code, request_body, remark, status)
VALUES ('builtin-video-generations-kling',
        '视频生成（Kling 文生视频，带计费档位）',
        'VIDEO_GENERATIONS',
        '{"model":"model-name","prompt":"一条小路穿过春天的森林，镜头缓慢向前推进","duration":5,"size":"1280x720","sound":false}',
        'Kling 文生视频：duration / size / sound 是视频计费的分档键，网关按它们取档位价。加 image 字段即变为图生视频（参考图张数进计费输入）。',
        1)
ON DUPLICATE KEY UPDATE remark = remark;

-- 自检：应返回 1 行
SELECT code, name, protocol_code, status
FROM kb_test_request_presets
WHERE code = 'builtin-video-generations-kling';
