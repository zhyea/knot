package org.chobit.knot.gateway.kafka;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.kafka.msg.Msg;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka 投递器：把 {@link Msg} 协议消息送到指定 topic，纯管道，不认识业务主题；topic 由调用方传入，分区键取 {@link Msg#traceId()}。
 * 序列化后 {@code kafkaTemplate.send()} 提交即返回，不等 broker 应答；投递异步性由生产者客户端承担，不建自建队列、不起投递线程。
 * 契约：序列化失败计 {@code dropped} 并告警，send 同步异常计 {@code dropped} 并落死信，异步回调失败落死信；异常不穿透到请求链路。
 * {@code kafka.mock=true} 时改用 {@link #logOnly()}：消息只打到 INFO 日志（前缀 {@code [kafka-mock]}）；{@link #warmUp(String...)} 在启动期预热元数据。
 */
@Slf4j
public class KafkaPublisher {

    private static final int WARN_LOG_EVERY = 100;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventDeadLetterWriter deadLetter;
    /**
     * 日志模式：为 {@code true} 时不持有生产者与死信写入器，消息只进日志。
     */
    private final boolean logOnly;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    /**
     * @param kafkaTemplate 本组件自用的生产者模板（producer props 见 {@link KafkaConfiguration}）
     * @param deadLetter    投递失败的兜底写入器
     */
    public KafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
                          EventDeadLetterWriter deadLetter) {
        this(kafkaTemplate, deadLetter, false);
    }

    private KafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
                           EventDeadLetterWriter deadLetter,
                           boolean logOnly) {
        this.kafkaTemplate = kafkaTemplate;
        this.deadLetter = deadLetter;
        this.logOnly = logOnly;
    }

    /**
     * 日志模式投递器：不连 broker，把序列化后的消息打到 INFO 日志。
     * 只由装配处在 {@code kafka.mock=true} 时使用，不写死信文件。
     *
     * @return 日志模式投递器（无生产者、无死信写入器）
     */
    public static KafkaPublisher logOnly() {
        return new KafkaPublisher(null, null, true);
    }

    /**
     * 投递一条消息（不等应答，结果由本类自行兜底）。
     *
     * @param topic 目标主题全名（由调用方维护）
     * @param msg   协议消息；分区键取 {@link Msg#traceId()}
     */
    public void publish(String topic, Msg msg) {
        String payload;
        try {
            payload = JsonKit.toJsonOrThrow(msg);
        } catch (JsonProcessingException e) {
            // ⚠ 旧实现用 toJson：序列化失败只 log 后返回 null，catch (RuntimeException) 永远抓不到，
            // 于是 send(topic, key, null) 发出 tombstone（compacted topic 上等于删掉同 key 消息），
            // sent 照常递增、死信不写——「绝不丢消息」的契约被静默打破。故改用受检异常版本。
            log.warn("Kafka message serialization failed, message dropped", e);
            dropped.incrementAndGet();
            return;
        }
        if (payload == null) {
            // msg 本身为 null：同样不能把 null 当 payload 发出去
            log.warn("Kafka message is null, message dropped");
            dropped.incrementAndGet();
            return;
        }
        String key = msg.traceId();
        if (logOnly) {
            // 单行 JSON 与真实投递的 payload 逐字一致：日志里看到什么，broker 上就是什么
            log.info("[kafka-mock] topic={} key={} payload={}", topic, key, payload);
            sent.incrementAndGet();
            return;
        }
        try {
            kafkaTemplate.send(topic, key, payload).whenComplete((result, failure) -> {
                if (failure != null) {
                    deadLetter.write(topic, key, payload, failure);
                }
            });
            sent.incrementAndGet();
        } catch (RuntimeException e) {
            // 同步失败（元数据/缓冲等待超时等）：消息没进 Kafka，落死信 + 计数，绝不上抛
            long total = dropped.incrementAndGet();
            if (total % WARN_LOG_EVERY == 1) {
                log.warn("Kafka send failed synchronously, message went to dead letter (total={})",
                        total, e);
            }
            deadLetter.write(topic, key, payload, e);
        }
    }

    /**
     * 启动期预热：主动拉取主题的分区信息，把请求线程上最可能发生的等待提前消化。
     * 失败只降级不抛——启动时 Kafka 不可用不该拖垮网关（由装配处调用，topic 由业务传入）。
     *
     * @param topics 需要预热的主题全名
     */
    public void warmUp(String... topics) {
        if (logOnly) {
            // 日志模式没有元数据可预热，也不能让调用方为「预热」去碰不存在的生产者
            return;
        }
        for (String topic : topics) {
            try {
                kafkaTemplate.partitionsFor(topic);
            } catch (RuntimeException e) {
                log.warn("Kafka metadata warm-up failed for topic {}, will retry lazily on first send",
                        topic, e);
            }
        }
    }

    /**
     * 已成功提交到 Kafka 生产者（无同步异常）的消息数；不含死信口径的死信数，见
     * {@link EventDeadLetterWriter#written()} / {@link EventDeadLetterWriter#lost()}。
     */
    public long sent() {
        return sent.get();
    }

    /**
     * 被丢弃的消息数（序列化失败 + send 同步失败）。
     */
    public long dropped() {
        return dropped.get();
    }
}
