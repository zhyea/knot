package org.chobit.knot.gateway.kafka;

import org.apache.kafka.common.PartitionInfo;
import org.chobit.knot.gateway.kafka.msg.Msg;
import org.chobit.knot.gateway.kafka.msg.RequestMsgDto;
import org.chobit.knot.gateway.kafka.msg.ResponseMsgDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka 投递器的边界：<b>纯管道——topic 调用方传、key 取 msg.traceId()、
 * 双路失败落死信、预热绝不抛，任何情况下 publish 不抛</b>。
 *
 * <p>JDK25 + Mockito inline 不能 mock 具体类，{@link KafkaTemplate} 用子类假件驱动。</p>
 */
class KafkaPublisherTest {

    private static final String TOPIC_REQUEST = "knot-gateway-request-event";
    private static final String TOPIC_RESPONSE = "knot-gateway-response-event";

    @Test
    void publishesMsgWithTraceIdAsKey(@TempDir Path dir) {
        FakeKafkaTemplate template = new FakeKafkaTemplate();
        KafkaPublisher publisher = new KafkaPublisher(template,
                new EventDeadLetterWriter(dir.resolve("dlq").toString()));

        publisher.publish(TOPIC_REQUEST, sampleRequest("trace-1"));
        publisher.publish(TOPIC_RESPONSE, sampleResponse("trace-1"));

        // Kafka 原生 async：调用返回即已提交，无需等待后台线程
        assertEquals(2, template.sentCount());
        assertEquals(TOPIC_REQUEST, template.sent(0).topic());
        assertEquals(TOPIC_RESPONSE, template.sent(1).topic());
        assertEquals("trace-1", template.sent(0).key(), "分区键取 msg.traceId()：两层消息必须同键同分区");
        assertEquals("trace-1", template.sent(1).key());
        assertTrue(template.sent(0).payload().contains("knot.gateway.request/1.0"));
        assertTrue(template.sent(1).payload().contains("knot.gateway.response/1.0"));
        assertEquals(2, publisher.sent());
        assertEquals(0, publisher.dropped());
    }

    @Test
    void syncFailureWritesDeadLetterAndCountsDropped(@TempDir Path dir) throws Exception {
        Path dlq = dir.resolve("dlq").resolve("events.jsonl");
        EventDeadLetterWriter writer = new EventDeadLetterWriter(dlq.toString());
        FakeKafkaTemplate template = new FakeKafkaTemplate();
        template.throwOnSend = true;
        KafkaPublisher publisher = new KafkaPublisher(template, writer);

        publisher.publish(TOPIC_RESPONSE, sampleResponse("trace-sync"));

        awaitFile(dlq);
        assertEquals(0, publisher.sent());
        assertEquals(1, publisher.dropped(), "同步失败 = 没进 Kafka，必须计数丢弃");
        assertEquals(1, writer.written());
        String line = Files.readAllLines(dlq).get(0);
        assertTrue(line.contains(TOPIC_RESPONSE));
        assertTrue(line.contains("trace-sync"), "死信里要能看出是哪条消息");
        assertTrue(line.contains("metadata unavailable"), "死信要带失败原因");
    }

    @Test
    void asyncFailureWritesDeadLetterWithoutThrowing(@TempDir Path dir) throws Exception {
        Path dlq = dir.resolve("dlq").resolve("events.jsonl");
        EventDeadLetterWriter writer = new EventDeadLetterWriter(dlq.toString());
        FakeKafkaTemplate template = new FakeKafkaTemplate();
        template.future = CompletableFuture.failedFuture(new RuntimeException("broker down"));
        KafkaPublisher publisher = new KafkaPublisher(template, writer);

        publisher.publish(TOPIC_RESPONSE, sampleResponse("trace-async"));

        awaitFile(dlq);
        // 异步失败在回调里才暴露：同步路径无异常，sent 照常 +1，兜底靠死信
        assertEquals(1, publisher.sent());
        assertEquals(0, publisher.dropped());
        assertEquals(1, writer.written());
        String line = Files.readAllLines(dlq).get(0);
        assertTrue(line.contains("trace-async"));
        assertTrue(line.contains("broker down"));
    }

    @Test
    void blankDlqPathCountsLostWithoutThrowing() {
        EventDeadLetterWriter writer = new EventDeadLetterWriter("");
        FakeKafkaTemplate template = new FakeKafkaTemplate();
        template.future = CompletableFuture.failedFuture(new RuntimeException("broker down"));
        KafkaPublisher publisher = new KafkaPublisher(template, writer);

        publisher.publish(TOPIC_RESPONSE, sampleResponse("trace-no-dlq"));

        assertEquals(0, writer.written());
        assertEquals(1, writer.lost(), "未配死信路径 = 真正的丢失，必须计数");
    }

    @Test
    void warmUpPullsMetadataForEveryTopic() {
        FakeKafkaTemplate template = new FakeKafkaTemplate();
        KafkaPublisher publisher = new KafkaPublisher(template, new EventDeadLetterWriter(""));

        publisher.warmUp(TOPIC_REQUEST, TOPIC_RESPONSE);

        assertEquals(List.of(TOPIC_REQUEST, TOPIC_RESPONSE), template.warmedTopics, "传入的主题都要预热");
    }

    @Test
    void warmUpSwallowsMetadataFailure() {
        FakeKafkaTemplate template = new FakeKafkaTemplate();
        template.throwOnWarmUp = true;
        KafkaPublisher publisher = new KafkaPublisher(template, new EventDeadLetterWriter(""));

        // 启动时 broker 不可用不该拖垮网关：预热失败只降级
        publisher.warmUp(TOPIC_REQUEST, TOPIC_RESPONSE);
    }

    @Test
    void msgNodeIdIsAutoDetected() {
        assertNotNull(sampleRequest("trace-node").nodeId());
        assertTrue(!sampleRequest("trace-node").nodeId().isBlank(),
                "Msg.nodeId 按主机名自动探测，无配置项");
    }

    private static RequestMsgDto sampleRequest(String traceId) {
        return new RequestMsgDto("knot.gateway.request/1.0", traceId, "span-1", Msg.NODE_ID,
                "2026-10-17T07:12:33+08:00", "CHAT_COMPLETIONS", Boolean.FALSE, null, null);
    }

    private static ResponseMsgDto sampleResponse(String traceId) {
        return new ResponseMsgDto("knot.gateway.response/1.0", traceId, "span-1", Msg.NODE_ID,
                "2026-10-17T07:12:33+08:00", "2026-10-17T07:12:38+08:00", 5310L, "success", 200,
                812L, null, List.of(), 0, 0, 0, false, null, null, null);
    }

    private static void awaitFile(Path path) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(path) && !Files.readAllLines(path).isEmpty()) {
                return;
            }
            Thread.sleep(20L);
        }
    }

    /**
     * KafkaTemplate 假件：行为由 {@code throwOnSend} / {@code throwOnWarmUp} / {@code future}
     * 三个字段驱动，不碰任何真实 producer。
     */
    private static class FakeKafkaTemplate extends KafkaTemplate<String, String> {

        private boolean throwOnSend;
        private boolean throwOnWarmUp;
        private CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        private final List<Sent> sent = new ArrayList<>();
        private final List<String> warmedTopics = new ArrayList<>();

        FakeKafkaTemplate() {
            super(new FakeProducerFactory());
        }

        @Override
        public synchronized CompletableFuture<SendResult<String, String>> send(String topic, String key,
                                                                               String payload) {
            if (throwOnSend) {
                throw new IllegalStateException("metadata unavailable");
            }
            sent.add(new Sent(topic, key, payload));
            return future;
        }

        @Override
        public synchronized List<PartitionInfo> partitionsFor(String topic) {
            if (throwOnWarmUp) {
                throw new IllegalStateException("metadata unavailable");
            }
            warmedTopics.add(topic);
            return List.of();
        }

        private synchronized int sentCount() {
            return sent.size();
        }

        private synchronized Sent sent(int index) {
            return sent.get(index);
        }
    }

    /**
     * ProducerFactory 假件：send 被覆写，永远走不到 createProducer。
     */
    private static final class FakeProducerFactory implements ProducerFactory<String, String> {

        @Override
        public org.apache.kafka.clients.producer.Producer<String, String> createProducer() {
            return null;
        }
    }

    private record Sent(String topic, String key, String payload) {
    }
}
