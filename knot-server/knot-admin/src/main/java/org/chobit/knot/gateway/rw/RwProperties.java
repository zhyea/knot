package org.chobit.knot.gateway.rw;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 与 <a href="https://github.com/zhyea/zhy-spring-boot-starter">zhy-spring-boot-starter</a> 中 {@code rw} 前缀配置对齐的开关项。
 */
@ConfigurationProperties(prefix = "rw")
public class RwProperties {

    /** 是否启用返回值包装 Advice */
    private boolean enabled = true;

    /** 成功时写入 {@link org.chobit.knot.gateway.ApiResponse#code()}，便于与数值型错误码体系统一 */
    private int successCode = 0;

    /** 全局异常处理器写入的失败码 */
    private int failCode = 10000;

    /**
     * 静默模式：为 true 时所有 {@link org.springframework.web.bind.annotation.RestController}
     * 的返回值（除已排除类型）均包装为 {@link org.chobit.knot.gateway.ApiResponse}；
     * 为 false 时仅对标注了 {@link ResponseWrapper} 的类/方法生效。
     */
    private boolean silentMode = true;

    /** 可选：写入所有成功响应的 {@link org.chobit.knot.gateway.ApiResponse#tags()} */
    private List<String> tags = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSuccessCode() {
        return successCode;
    }

    public void setSuccessCode(int successCode) {
        this.successCode = successCode;
    }

    public int getFailCode() {
        return failCode;
    }

    public void setFailCode(int failCode) {
        this.failCode = failCode;
    }

    public boolean isSilentMode() {
        return silentMode;
    }

    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }
}
