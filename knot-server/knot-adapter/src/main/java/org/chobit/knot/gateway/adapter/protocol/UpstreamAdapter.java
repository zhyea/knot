package org.chobit.knot.gateway.adapter.protocol;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.http.MediaType;

import java.util.Set;

/**
 * 上游适配器：把网关侧请求发往上游所需的<b>双向适配</b>角色——同时负责
 * <b>请求适配</b>（{@link #resolvePath} 协议路径 / {@link #buildRequestBody} 请求体转换 /
 * {@link #resolveContentType} 内容类型）与<b>响应适配</b>（{@link #handleResponse} 整包响应体转换 /
 * {@link #streamAdapter} 流式逐行转换）。
 *
 * <p>不存在「只适配请求」或「只适配响应」的拆分角色：一次协议转换的两个方向必须由同一个
 * 实现持有（如 Bedrock Converse：请求侧 chat→converse、响应侧 converse→chat，口径才能对齐），
 * 拆开只会让两侧各漂移一份。</p>
 *
 * <p>命名规则（10-backend-java §4）：{@code <供应商>+<原协议>+<新协议>+UpstreamAdapter}；
 * 泛化转换（不隶属单一供应商，如 ChatCompletions→Messages）免供应商段；透传实现名为
 * {@code PassthroughUpstreamAdapter}。code 即 {@code kb_model_api_bindings.request_adapter}
 * 的绑定值。</p>
 */
public interface UpstreamAdapter {

    String code();

    String label();

    Set<ModelApiProtocolEnum> protocol();

    /**
     * 协议路径：优先绑定级 {@code api_path}，缺省走协议默认路径。
     *
     * <p>不处理模型级 {@code base_path}——那是 URL 前缀，由执行器统一拼接，
     * 避免每个适配器重复实现同样的拼接逻辑。</p>
     */
    default String resolvePath(UpstreamRequestContext context, String defaultPath) {
        String bindingPath = context.binding() == null ? null : context.binding().apiPath();
        return StringUtils.defaultIfBlank(StringUtils.trim(bindingPath), defaultPath);
    }

    /**
     * 该适配器能否承接流式请求。默认 {@code true}；AWS Bedrock 的流式是 EventStream 二进制帧
     * （不是 SSE），P1 返回 {@code false} 让执行器走整包路径。
     */
    default boolean streamSupported(UpstreamRequestContext context) {
        return true;
    }

    Object buildRequestBody(UpstreamRequestContext context);

    default MediaType resolveContentType(UpstreamRequestContext context) {
        return context.contentType();
    }

    /**
     * 响应适配：把上游响应体转换成网关侧（客户端）形状。默认原样透传。
     *
     * <p>整包路径上，用量提取吃的就是本方法的产物——响应侧转换同时定义了计费侧的
     * 输入形状，两个方向在此收口。</p>
     */
    default String handleResponse(String responseBody, UpstreamRequestContext context) {
        return responseBody;
    }

    /**
     * 流式响应适配器：把上游 SSE 逐行转换成网关侧（客户端）形状。
     *
     * <p>与 {@link #handleResponse} 按执行路径分工：后者只服务整包路径，本方法只服务流式
     * 逐事件路径——两条路径的转换产物都同时喂给客户端转发与用量提取（同构：提取吃的
     * 永远是转换后的数据）。声明 {@link #streamSupported} 为 {@code false} 的适配器
     * （如 Bedrock，上游是 EventStream 二进制帧）永远不走本方法。</p>
     *
     * <p><b>每次请求新建实例</b>：流式转换天然有跨事件状态（{@code message_start} 只许合成一次、
     * chunk id 必须全程一致、工具块序号要连续），而适配器本身是单例 Bean——状态挂在适配器上
     * 会被并发请求互相污染。默认返回 {@link StreamLineAdapter#PASSTHROUGH}：不覆写时零解析、
     * 零序列化，流式转发逐字节不变。</p>
     *
     * @param context 本次请求的上下文（可为 {@code null}，实现需自担空判断）
     */
    default StreamLineAdapter streamAdapter(UpstreamRequestContext context) {
        return StreamLineAdapter.PASSTHROUGH;
    }

}
