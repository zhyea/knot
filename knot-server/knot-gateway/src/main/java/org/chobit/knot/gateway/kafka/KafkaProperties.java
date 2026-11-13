package org.chobit.knot.gateway.kafka;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka 事件输出配置（顶层 {@code kafka.*}），环境变量一律 {@code KAFKA_*}；事件内容是数仓多维统计的事实来源。
 * 默认 {@code enabled=false}，整套组件不装配。
 * 埋点由调用线程 {@code kafkaTemplate.send()} 提交，不建内存队列、不起后台线程；缓冲等待由 {@code max.block.ms=1000} 封顶，失败落死信文件，启动期 {@link KafkaPublisher#warmUp(String...)} 预热元数据。
 * topic 写全名、不拼前缀，契约版本在消息 {@code schema} 字段里演进，主题名保持稳定。
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
     * 本地测试开关（{@code kafka.mock}）：{@code true} 时事件不投递 broker，只把序列化后的消息打到 INFO 日志（前缀 {@code [kafka-mock]}）。
     * 开启后不装配生产者与死信写入器，不需要 {@code bootstrap-servers}；仅在 {@link #enabled} 为 {@code true} 时有意义。
     */
    private boolean mock = false;

    /**
     * Kafka 集群地址；开启且非 mock 时必须配置。
     */
    private String bootstrapServers = "";

    /**
     * 各用途 Kafka 主题全名（按业务语义分键，便于后续扩展更多主题）。
     */
    private Topic topic = new Topic();

    /**
     * 本地死信文件路径（append 写入 JSONL）；默认开启：直推失败即落盘，
     * 留空表示不落盘，只在日志里计数告警。
     */
    private String dlqPath = "logs/kafka-dlq";

    /**
     * Kafka 主题命名（按用途分键）。
     * 每个用途一个独立键（如 {@code traffic}），新增主题只加一个字段、不动既有键，
     * 主题名写全名不拼前缀；契约版本在消息的 {@code schema} 字段里演进，主题名保持稳定。
     */
    @Getter
    @Setter
    public static class Topic {

        /** 流量事件主题全名（请求入口 + 处置结果合并为单条消息，终态一次发出）。 */
        private String traffic = "knot-gateway-traffic-event";
    }
}
