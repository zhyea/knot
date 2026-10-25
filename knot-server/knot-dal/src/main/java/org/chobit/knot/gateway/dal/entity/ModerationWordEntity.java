package org.chobit.knot.gateway.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词条：Aho-Corasick 自动机的数据源。
 *
 * <p>分类（{@code category_code}）指向 {@code ks_enum_configs.item_code}，仅作<b>标签维度</b>：
 * 是否参与匹配只看本行 {@code status} / {@code is_deleted}，不因分类停用而失效。
 * 严重度挂在词条上而非分类：同一分类下不同词严重度不同。</p>
 */
@Data
public class ModerationWordEntity {
    private Long id;
    private String word;
    /** 归一化键（NFKC + 小写 + 去零宽）；判定走它、展示走 word */
    private String normalizedWord;
    private String categoryCode;
    /** 严重度：1-低 2-中 3-高 */
    private Integer severity;
    private String lang;
    private Integer status;
    private Integer isDeleted;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
