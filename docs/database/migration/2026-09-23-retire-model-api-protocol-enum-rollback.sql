-- 2026-09-23 接口协议枚举退役 - 回滚脚本

INSERT IGNORE INTO ks_enum_categories
    (id, category, category_name, is_system, is_enabled)
VALUES
    (22, 'model_api_protocol', '模型接口类型', 0, 1);

INSERT IGNORE INTO ks_enum_configs
    (category_id, item_code, item_label, sort_order, is_enabled)
VALUES
    (22, 'CHAT_COMPLETIONS', 'Chat Completions', 1, 1),
    (22, 'RESPONSES', 'Responses', 2, 1),
    (22, 'MESSAGES', 'Messages', 3, 1),
    (22, 'COMPLETIONS', 'Completions', 4, 1),
    (22, 'EMBEDDINGS', 'Embeddings', 5, 1),
    (22, 'IMAGE_GENERATIONS', 'Image Generations', 6, 1),
    (22, 'IMAGE_EDITS', 'Image Edits', 7, 1),
    (22, 'IMAGE_VARIATIONS', 'Image Variations', 8, 1),
    (22, 'AUDIO_TRANSCRIPTIONS', 'Audio Transcriptions', 9, 1),
    (22, 'AUDIO_TRANSLATIONS', 'Audio Translations', 10, 1),
    (22, 'AUDIO_SPEECH', 'Audio Speech', 11, 1),
    (22, 'VIDEO_GENERATIONS', 'Video Generations', 12, 1),
    (22, 'RERANK', 'Rerank', 13, 1),
    (22, 'MODERATIONS', 'Moderations', 14, 1),
    (22, 'CUSTOM', '自定义', 99, 1);
