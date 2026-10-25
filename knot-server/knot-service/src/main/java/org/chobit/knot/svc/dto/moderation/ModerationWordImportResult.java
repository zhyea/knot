package org.chobit.knot.svc.dto.moderation;

import java.util.List;

/**
 * 批量导入结果：逐行汇总，**部分失败不影响已成功的行**（导入是长尾运维操作，
 * 一行脏数据不能让整批万级词库回滚）。
 */
public record ModerationWordImportResult(int total,
                                         int inserted,
                                         int skipped,
                                         List<String> errors) {

    public static ModerationWordImportResult of(int total, int inserted, int skipped, List<String> errors) {
        return new ModerationWordImportResult(total, inserted, skipped, errors == null ? List.of() : errors);
    }
}
