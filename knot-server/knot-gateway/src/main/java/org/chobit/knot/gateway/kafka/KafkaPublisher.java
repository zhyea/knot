package org.chobit.knot.gateway.kafka;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.kafka.msg.Msg;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka 投递器：把 {@link Msg} 协议消息送到指定 topic——**纯管道，不认识任何业务概念**。
 *
 * <p>只有一个投递方法 {@link #publish(String, Msg)}：topic 由调用方（业务埋点侧）维护并
 * 传入，本类不持有「请求层 / 响应层」这类业务主题常量；分区键取消息自带的
 * {@link Msg#traceId()}。</p>
 *
 * <h3>形态：Kafka 原生 async + fire-and-forget（robin 2026-10-18 拍板「是 kafka 的 async」）</h3>
 * <p>序列化后 {@code kafkaTemplate.send()} 提交即返回，<b>不等 broker 应答</b>——埋点穿插在
 * 上游调用前后，同步等 ack 会把 broker 的 RTT 算进 TTFT，这是请求链路的红线。投递异步性
 * 由生产者客户端承担（accumulator 缓冲 + sender 后台线程），不建自建队列、不起投递线程
 * （队列化要么满即丢弃 = 丢数据、要么无界堆积 = OOM、要么反压请求线程 = 拖慢 TTFT）。</p>
 *
 * <h3>契约：绝不抛</h3>
 * <p>序列化失败（这条消息废了）→ {@code dropped} + debug；
 * send 同步抛异常（如 {@code max.block.ms} 超时）→ {@code dropped} + 节流 WARN + 死信；
 * 异步回调失败（broker 拒收 / 网络抖动，回调里才暴露）→ 死信（written/lost 计数）。
 * <b>任何情况下异常都不穿透到请求链路。</b></p>
 *
 * <p>TTFT 风险由生产者客户端的 {@code max.block.ms=1000} 封顶（见 {@link KafkaConfiguration}）：
 * Kafka 持续故障且 accumulator（默认 32MB）也满时调用线程最多阻塞 1s/事件——有界但真实存在，
 * 属已披露的取舍；{@link #warmUp(String...)} 把最常见的触发场景（启动后首次发送时元数据
 * 未就绪）提前消化到启动期。</p>
 */
@Slf4j
public class KafkaPublisher {

    private static final int WARN_LOG_EVERY = 100;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventDeadLetterWriter deadLetter;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    /**
     * Constructs a new instance.
     *
     * @param kafkaTemplate 本组件自用的生产者模板（producer props 见 {@link KafkaConfiguration}）
     * @param deadLetter    投递失败的兜底写入器
     */
    public KafkaPublisher(KafkaTemplate<String, String> kafkaTemplate,
                          EventDeadLetterWriter deadLetter) {
        this.kafkaTemplate = kafkaTemplate;
        this.deadLetter = deadLetter;
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
            payload = JsonKit.toJson(msg);
        } catch (RuntimeException e) {
            // 序列化失败只是这条消息废了，绝不影响请求链路
            log.debug("Kafka message serialization failed, message dropped", e);
            dropped.incrementAndGet();
            return;
        }
        String key = msg.traceId();
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
