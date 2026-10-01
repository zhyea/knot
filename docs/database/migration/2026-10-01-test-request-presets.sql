-- 2026-10-01 路由调试预设请求用例表
-- 替代 RoutingRuleService#defaultRequestBody 的硬编码骨架。
-- 由用户维护完整、具体的请求体 JSON，按协议归类、全局共享复用。
CREATE TABLE IF NOT EXISTS kb_test_request_presets (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code          VARCHAR(64)  NOT NULL COMMENT '预设编码，人工填写，唯一',
    name          VARCHAR(100) NOT NULL COMMENT '预设名称',
    protocol_code VARCHAR(64)  NOT NULL COMMENT '关联协议 canonical code，如 CHAT_COMPLETIONS',
    request_body  LONGTEXT     NOT NULL COMMENT '完整请求体 JSON（具体值；model/prompt 由调试面板按目标覆盖）',
    remark        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_test_request_presets_code (code)
) COMMENT = '路由调试预设请求用例（替代硬编码默认请求体）';
