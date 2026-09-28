-- 外部模型忽略状态：忽略的模型在列表中排到最后，并可恢复显示。
ALTER TABLE kx_model_items
    ADD COLUMN ignored TINYINT NOT NULL DEFAULT 0 AFTER logical_model_id;

CREATE INDEX idx_external_model_ignored ON kx_model_items (ignored);
