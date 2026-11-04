package org.chobit.knot.gateway.kafka;

import org.chobit.knot.gateway.kafka.msg.Msg;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka 装配的 mock 分支：<b>{@code kafka.mock=true} 让整条埋点链路在没有 broker 时可用</b>。
 *
 * <p>两个方向都要证：mock 免地址能起来；非 mock 缺地址必须启动失败——否则「开了通道却不知道
 * 往哪发」会退化成静默管道。</p>
 */
class KafkaConfigurationTest {

    private static final String TRAFFIC = "knot-gateway-traffic-event";

    @Test
    void mockModeSkipsProducerAndDeadLetter() {
        try (AnnotationConfigApplicationContext ctx = context(true)) {
            assertEquals(0, ctx.getBeanNamesForType(KafkaTemplate.class).length,
                    "mock 下不建生产者：没有 broker 可连，建了只会刷重试日志");
            assertEquals(0, ctx.getBeanNamesForType(EventDeadLetterWriter.class).length,
                    "mock 下消息不进 broker，也就不存在投递失败");

            KafkaPublisher publisher = ctx.getBean(KafkaPublisher.class);
            publisher.warmUp(TRAFFIC);
            publisher.publish(TRAFFIC, new ProbeMsg("trace-mock"));
            assertEquals(1, publisher.sent());
            assertEquals(0, publisher.dropped());
        }
    }

    @Test
    void nonMockWithoutBootstrapServersFailsFast() {
        // 唯一必须暴露的配置错误：开了通道却不知道往哪发
        BeanCreationException e = assertThrows(BeanCreationException.class, () -> context(false));
        String message = e.getMostSpecificCause().getMessage();
        assertTrue(message.contains("bootstrap-servers"), message);
    }

    /**
     * 只装配 {@link KafkaConfiguration}（不加载 Spring Boot 自动配置）：mock 分支是否成立
     * 与「容器里有没有别的 kafka bean」无关。
     */
    private static AnnotationConfigApplicationContext context(boolean mock) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        Map<String, Object> props = new HashMap<>();
        props.put("kafka.enabled", "true");
        props.put("kafka.mock", String.valueOf(mock));
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
        ctx.register(KafkaConfiguration.class);
        ctx.refresh();
        return ctx;
    }

    /**
     * 最小 {@link Msg}：mock 模式只关心 traceId 作为 key 落进日志。
     */
    private record ProbeMsg(String traceId) implements Msg {
    }
}
