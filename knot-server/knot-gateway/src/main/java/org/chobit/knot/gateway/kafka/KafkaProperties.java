package org.chobit.knot.gateway.kafka;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka 事件输出配置（顶层 {@code kafka.*}，与 {@code mybatis} 平级）。
 *
 * <p>Kafka 是系统基础组件，不属于任何业务域，因此配置提到顶层、不走 {@code knot.*} 业务域
 * 命名空间；环境变量一律 {@code KAFKA_*}（不带 {@code KNOT_} 前缀）。
 * 事件内容是数仓多维统计的事实来源（契约见
 * {@code docs/gateway-warehouse-kafka-message-protocol-2026-10-17.md}）。
 *
 * <p><b>默认关闭</b>：{@code enabled=false} 时整套组件不装配，网关行为与本项目之前完全一致，
 * 不影响开发库与冒烟。</p>
 *
 * <p><b>投递形态：Kafka 原生 async（2026-10-18 robin 拍板「是 kafka 的 async」）。</b>
 * 埋点序列化后由调用线程 {@code kafkaTemplate.send()} 提交，<b>不建内存队列、不起后台线程</b>——
 * 投递异步性完全由生产者客户端承担（accumulator 缓冲 + sender 后台线程，请求线程不等 ack）。
 * 自建队列被否决的原因：「数据宝贵，不能丢失」，而队列化要么满即丢弃（丢数据）、
 * 要么无界堆积（OOM）、要么反压请求线程（拖慢 TTFT），三条路都不可接受。
 * 缓冲等待有界化交给生产者客户端的 {@code max.block.ms=1000}
 * （等待元数据 / 缓冲的最长时间，超时即本次发送失败），失败一律落死信文件；
 * 启动期 {@link KafkaPublisher#warmUp(String...)} 预热元数据，消化最常见的等待场景。</p>
 *
 * <p><b>topic 写全名</b>：不拼前缀，一条完整主题名；契约版本在消息的
 * {@code schema} 字段里演进，主题名保持稳定。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

    /**
     * 总开关；{@code false} 时 {@link KafkaPublisher} 不装配，埋点侧拿到的是 {@code null}。
     */
    private boolean enabled = false;

    /**
     * Kafka 集群地址；开启时必须配置。
     */
    private String bootstrapServers = "";

    /**
     * 各用途 Kafka 主题全名（按业务语义分键，便于后续扩展更多主题）。
     */
    private Topic topic = new Topic();

    /**
     * 本地死信文件路径（append 写入 JSONL）；<b>默认开启</b>：直推失败即落盘，
     * 留空表示不落盘，只在日志里计数告警。
     */
    private String dlqPath = "logs/kafka-dlq";

    /**
     * Kafka 主题命名（按用途分键）。
     *
     * <p>每个用途一个独立键（如 {@code traffic}），新增主题只加一个字段、不动既有键，
     * 主题名写全名不拼前缀；契约版本在消息的 {@code schema} 字段里演进，主题名保持稳定。</p>
     */
    @Getter
    @Setter
    public static class Topic {

        /**
         * 流量事件主题全名（请求入口 + 处置结果合并为单条消息，终态一次发出）。
         *
         * <p>2026-10-18 合并：原请求层 / 响应层两个主题收敛为一个——数仓离线批处理，
         * 不再需要早期「实时流量入口」信号，单主题单消息即可。</p>
         */
        private String traffic = "knot-gateway-traffic-event";
    }
}
