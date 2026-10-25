package org.chobit.knot.svc.dto.moderation;

/**
 * 批量导入的一行：只带业务字段，归一化键、状态、时间由服务端决定。
 */
public record ModerationWordImportItem(String word,
                                       String categoryCode,
                                       Integer severity,
                                       String lang,
                                       String remark) {
}
