package org.chobit.knot.gateway.rw;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 与 zhy-spring-boot-starter 中 {@code rw} 前缀配置对齐的包装配置项。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "rw")
public class RwProperties {

    /** 是否启用统一返回包装。 */
    private boolean enabled = true;

    /** 成功时写入 {@link org.chobit.knot.gateway.ApiResponse#code()} 的业务码。 */
    private int successCode = 0;

    /** 全局异常处理器写入的默认失败码。 */
    private int failCode = 10000;

    /**
     * 静默模式。
     * 为 true 时所有 {@link org.springframework.web.bind.annotation.RestController}
     * 的返回值都会包装为 {@link org.chobit.knot.gateway.ApiResponse}；
     * 为 false 时仅对标注了 {@link ResponseWrapper} 的类或方法生效。
     */
    private boolean silentMode = true;

    /** 默认写入 {@link org.chobit.knot.gateway.ApiResponse#tags()} 的标签。 */
    private List<String> tags = new ArrayList<>();

    /**
     * Updates the default tags.
     *
     * <p>手写保留：null 入参归一为空列表， Lombok 生成的 setter 会原样置 null。
     */
    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }
}
