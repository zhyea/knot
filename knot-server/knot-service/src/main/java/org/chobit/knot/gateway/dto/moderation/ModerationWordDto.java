package org.chobit.knot.gateway.dto.moderation;

import java.time.LocalDateTime;

/**
 * 敏感词条 DTO：管理面 CRUD 的入参与内部承载。
 */
public record ModerationWordDto(Long id,
                                String word,
                                String categoryCode,
                                Integer severity,
                                String lang,
                                boolean enabled,
                                boolean deleted,
                                String remark,
                                /** 服务端算出的归一化键，不回传前端 */
                                String normalizedWord,
                                LocalDateTime createdAt,
                                LocalDateTime updatedAt) {
}
