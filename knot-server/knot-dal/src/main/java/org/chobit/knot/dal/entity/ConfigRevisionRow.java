package org.chobit.knot.dal.entity;

import lombok.Data;

/**
 * 配置表变更指纹投影行，{@code ConfigRevisionMapper} 的统一返回类型。
 *
 * <p>一行代表一张配置表：{@code rowCount} 是当前行数，{@code revision} 是内容指纹。
 * 有 {@code updated_at} 的表取最大更新时间；无该列的表取全列 CRC 聚合。
 * 指纹只用于判定「本表内容是否变化」，不承载业务含义，也不对外暴露。</p>
 */
@Data
public class ConfigRevisionRow {

    /** 配置表名，与 SQL 中硬编码的表名一致。 */
    private String tableName;

    /**
     * 当前行数：捕获物理删除与新增。
     *
     * <p>{@code updated_at} 对 {@code DELETE} 不留下痕迹，只看时间戳会漏掉删行。</p>
     */
    private Long rowCount;

    /** 内容指纹：与上一次探测不同即代表该表内容已变。 */
    private String revision;
}
