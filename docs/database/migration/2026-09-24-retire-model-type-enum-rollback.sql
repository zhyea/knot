-- 2026-09-24 模型类型枚举退役 - 回滚脚本

INSERT IGNORE INTO ks_enum_categories
    (id, category, category_name, is_system, is_enabled)
VALUES
    (2, 'model_type', '模型类型', 0, 1);

INSERT IGNORE INTO ks_enum_configs
    (category_id, item_code, item_label, sort_order, is_enabled)
VALUES
    (2, 'CHAT',       '对话',     1, 1),
    (2, 'TEXT',       '文本',     2, 1),
    (2, 'REASONING',  '推理',     3, 1),
    (2, 'MULTIMODAL', '多模态',   4, 1),
    (2, 'EMBEDDING',  '向量',     5, 1),
    (2, 'RERANK',     '重排',     6, 1),
    (2, 'IMAGE',      '图像',     7, 1),
    (2, 'AUDIO',      '语音',     8, 1),
    (2, 'VIDEO',      '视频',     9, 1),
    (2, 'DOCUMENT',   '文档理解', 10, 1),
    (2, 'OCR',        'OCR',      11, 1),
    (2, 'MODERATION', '安全审核', 12, 1),
    (2, 'UTILITY',    '工具辅助', 13, 1);
