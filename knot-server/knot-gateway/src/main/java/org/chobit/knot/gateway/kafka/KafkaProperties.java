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
 * <p><b>topic 写全名</b>：不拼前缀，请求层 / 响应层各一个完整主题名；契约版本在消息的
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
     * 请求层主题全名。
     */
    private String topicRequest = "knot-gateway-request-event";

    /**
     * 响应层主题全名。
     */
    private String topicResponse = "knot-gateway-response-event";

    /**
     * 本地死信文件路径（append 写入 JSONL）；<b>默认开启</b>：直推失败即落盘，
     * 留空表示不落盘，只在日志里计数告警。
     */
    private String dlqPath = "logs/kafka-dlq";
}
