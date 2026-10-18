package org.chobit.knot.gateway.kafka.msg;

import org.chobit.knot.gateway.kafka.KafkaPublisher;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 数仓消息协议超类：所有进出 Kafka 的顶层消息都实现它。
 *
 * <p>两个契约成员：{@link #traceId()} 是消息的分区键与数仓关联键，由消息自带；
 * {@link #nodeId()} 是产出节点标识，按主机名自动探测（接口常量，类加载时解析一次，
 * 不设配置项——主机名对单实例本就唯一，admin 侧 {@code NodeIdentity} 同走自动探测）。
 * 拿不到主机名时退化为 {@value #UNKNOWN_NODE}，宁可退化也不能让发布失败。</p>
 */
public interface Msg {

    String UNKNOWN_NODE = "unknown";

    /**
     * 消息关联键（W3C traceparent 派生）：请求层 / 响应层同键 → 同分区保序，
     * 也是 {@link KafkaPublisher#publish} 的分区键。
     */
    String traceId();

    /**
     * 产出节点标识。
     */
    default String nodeId() {
        return NODE_ID;
    }

    /**
     * 节点标识常量：接口初始化时按主机名解析一次。
     */
    String NODE_ID = resolveNodeId();

    private static String resolveNodeId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            return host == null || host.isBlank() ? UNKNOWN_NODE : host;
        } catch (RuntimeException | UnknownHostException e) {
            return UNKNOWN_NODE;
        }
    }
}
