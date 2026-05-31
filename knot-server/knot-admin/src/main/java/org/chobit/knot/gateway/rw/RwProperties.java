package org.chobit.knot.gateway.rw;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 涓?<a href="https://github.com/zhyea/zhy-spring-boot-starter">zhy-spring-boot-starter</a> 涓?{@code rw} 鍓嶇紑閰嶇疆瀵归綈鐨勫紑鍏抽」銆?
 */
@ConfigurationProperties(prefix = "rw")
public class RwProperties {

    /** 鏄惁鍚敤杩斿洖鍊煎寘瑁?Advice */
    private boolean enabled = true;

    /** 鎴愬姛鏃跺啓鍏?{@link org.chobit.knot.gateway.ApiResponse#code()}锛屼究浜庝笌鏁板€煎瀷閿欒鐮佷綋绯荤粺涓€ */
    private int successCode = 0;

    /** 鍏ㄥ眬寮傚父澶勭悊鍣ㄥ啓鍏ョ殑澶辫触鐮?*/
    private int failCode = 10000;

    /**
     * 闈欓粯妯″紡锛氫负 true 鏃舵墍鏈?{@link org.springframework.web.bind.annotation.RestController}
     * 鐨勮繑鍥炲€硷紙闄ゅ凡鎺掗櫎绫诲瀷锛夊潎鍖呰涓?{@link org.chobit.knot.gateway.ApiResponse}锛?
     * 涓?false 鏃朵粎瀵规爣娉ㄤ簡 {@link ResponseWrapper} 鐨勭被/鏂规硶鐢熸晥銆?
     */
    private boolean silentMode = true;

    /** 鍙€夛細鍐欏叆鎵€鏈夋垚鍔熷搷搴旂殑 {@link org.chobit.knot.gateway.ApiResponse#tags()} */
    private List<String> tags = new ArrayList<>();

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public int getSuccessCode() {
        return successCode;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void setSuccessCode(int successCode) {
        this.successCode = successCode;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public int getFailCode() {
        return failCode;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void setFailCode(int failCode) {
        this.failCode = failCode;
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isSilentMode() {
        return silentMode;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }
}
