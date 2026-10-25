package org.chobit.knot.svc.vo.moderation;

import java.time.LocalDateTime;

/**
 * 敏感词条管理面视图。
 *
 * <p>不暴露 {@code normalizedWord}：归一化键由服务端按 {@code word} 现算，前端只维护原文，
 * 避免两侧算法不一致导致判定与展示脱节。</p>
 */
public record ModerationWordItem(Long id,
                                 String word,
                                 String categoryCode,
                                 /** 严重度：1-低 2-中 3-高 */
                                 Integer severity,
                                 String lang,
                                 boolean enabled,
                                 boolean deleted,
                                 String remark,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
}
