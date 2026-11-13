package org.chobit.knot.gateway.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 事件通道装配：只有 {@code kafka.enabled=true} 时才装配。
 * 开启但缺 {@code bootstrap-servers} 时启动失败。
 * 生产者只配置地址、序列化器与等待封顶，其余走 spring-kafka / Kafka 默认值。
 * {@code kafka.mock=true} 时只装配把消息打到日志的 {@link KafkaPublisher}，生产者与死信写入器不进容器。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "kafka.enabled", havingValue = "true")
public class KafkaConfiguration {

    /** 投递等待封顶：{@code max.block.ms} 是 send 调用点拿元数据 / 等缓冲的最长阻塞时间，取 1000ms，超时即本次发送失败并落死信文件。 */
    private static final String MAX_BLOCK_MS = "1000";

    /**
     * 本组件自用的生产者模板；与 Spring Boot 自动配置的 {@code kafkaTemplate} 是不同 bean，互不影响（本模板带本项目配置的 {@code bootstrap-servers}）。
     * {@code kafka.mock=true} 时不装配。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "kafka.mock", havingValue = "false", matchIfMissing = true)
    public KafkaTemplate<String, String> kafkaEventTemplate(KafkaProperties properties) {
        if (properties.getBootstrapServers() == null || properties.getBootstrapServers().isBlank()) {
            throw new IllegalStateException(
                    "kafka.enabled=true requires kafka.bootstrap-servers to be configured");
        }
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers().trim());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /**
     * 死信写入器：投递失败（同步异常与异步回调失败两路）的统一兜底。
     * {@code kafka.mock=true} 时不装配。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "kafka.mock", havingValue = "false", matchIfMissing = true)
    public EventDeadLetterWriter eventDeadLetterWriter(KafkaProperties properties) {
        return new EventDeadLetterWriter(properties.getDlqPath());
    }

    /**
     * 投递器：纯管道，不认识业务主题，topic 由调用方维护并传入 {@code publish}；启动期预热流量事件主题的元数据。
     * mock 模式下按 {@link ObjectProvider} 取用上游 bean；非 mock 时缺失即启动失败。
     */
    @Bean
    public KafkaPublisher kafkaPublisher(ObjectProvider<KafkaTemplate<String, String>> kafkaEventTemplate,
                                         ObjectProvider<EventDeadLetterWriter> eventDeadLetterWriter,
                                         KafkaProperties properties) {
        if (properties.isMock()) {
            log.warn("Kafka event output is in MOCK mode: messages go to log only "
                    + "(prefix [kafka-mock], no broker, no dead letter); topic={}",
                    properties.getTopic().getTraffic());
            return KafkaPublisher.logOnly();
        }
        KafkaPublisher publisher = new KafkaPublisher(kafkaEventTemplate.getObject(),
                eventDeadLetterWriter.getObject());
        publisher.warmUp(properties.getTopic().getTraffic());
        log.info("Kafka event output enabled (kafka native async): bootstrapServers={}, topic={}, "
                        + "dlqPath={}",
                properties.getBootstrapServers(), properties.getTopic().getTraffic(), properties.getDlqPath());
        return publisher;
    }
}
