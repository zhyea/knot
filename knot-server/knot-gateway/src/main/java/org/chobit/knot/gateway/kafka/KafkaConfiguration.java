package org.chobit.knot.gateway.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 事件通道装配：<b>只有显式开启才装配</b>。
 *
 * <p>开关有两个作用：① 未部署 Kafka 的环境（开发库、冒烟）组件完全不进容器，
 * 网关行为与开启前逐字一致；② 开了但没配 {@code bootstrap-servers} 直接启动失败——
 * 「开了功能却不知道往哪发」必须暴露，不能变成一条永不落地的静默管道。</p>
 *
 * <p>生产者只写<b>必需项</b>（地址 + 序列化器 + 等待封顶），其余一律走
 * spring-kafka / Kafka 默认值，不在此处复述或微调客户端参数。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "kafka.enabled", havingValue = "true")
public class KafkaConfiguration {

    /**
     * 投递等待封顶：{@code max.block.ms} 是 send 调用点为「拿元数据 / 等缓冲」阻塞的最长时间，
     * 超时即本次发送失败 → 死信文件。取 1000ms：把 Kafka 故障时请求线程的阻塞预算钉在有界值。
     *
     * <p>注：kafka-clients 3.7.2 已删除 {@code block.on.buffer.full}，「缓冲满时立即失败」
     * 这个选项不存在，本项是唯一可用的杠杆。</p>
     */
    private static final String MAX_BLOCK_MS = "1000";

    /**
     * 本组件自用的生产者模板；与 Spring Boot 自动配置的 {@code kafkaTemplate} 是不同 bean，
     * 互不影响（本模板带本项目配置的 bootstrap-servers）。
     */
    @Bean
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
     */
    @Bean
    public EventDeadLetterWriter eventDeadLetterWriter(KafkaProperties properties) {
        return new EventDeadLetterWriter(properties.getDlqPath());
    }

    /**
     * 投递器：纯管道（不认识业务主题，topic 由调用方维护并传入 publish）；
     * 启动期在此预热两个主题的元数据（装配域一次性使用配置里的主题名，
     * 投递器本身不持有主题常量）。
     */
    @Bean
    public KafkaPublisher kafkaPublisher(KafkaTemplate<String, String> kafkaEventTemplate,
                                         EventDeadLetterWriter eventDeadLetterWriter,
                                         KafkaProperties properties) {
        KafkaPublisher publisher = new KafkaPublisher(kafkaEventTemplate, eventDeadLetterWriter);
        publisher.warmUp(properties.getTopicRequest(), properties.getTopicResponse());
        log.info("Kafka event output enabled (kafka native async): bootstrapServers={}, topicRequest={}, "
                        + "topicResponse={}, dlqPath={}",
                properties.getBootstrapServers(), properties.getTopicRequest(),
                properties.getTopicResponse(), properties.getDlqPath());
        return publisher;
    }
}
