package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AsyncTaskConverter} 的守卫。
 *
 * <p>⭐ <b>本测试存在的理由是「字段漏拷」这件事编译期不报错</b>：给
 * {@code AsyncTaskEntity} 加一列、忘了同步 {@link AsyncTaskDto}，
 * 表现是「新字段读出来恒为 null」，而且只在某个具体场景才显形——
 * 这类错误靠 code review 抓不住，只能靠逐字段断言。</p>
 *
 * <p>⭐ <b>「新任务的初值」也在这里钉</b>：{@code status} / {@code attempts} 的赋予权
 * 从 gateway 的 {@code AsyncTaskSupport#build} 搬到了 {@link AsyncTaskConverter#toEntity}
 * （因为数据访问整体归 svc，初值属于「怎么写库」的一部分）。
 * 按「<b>能力搬家时测试必须跟着搬家</b>」，钉 wire 值的断言也随之从
 * {@code AsyncTaskSupportTest} 迁到这里——留在那边的话，它断言的字段已经不存在，
 * 只会变成「删了能力测试还绿」的假绿。</p>
 *
 * <p>⚠ 断言里写<b>裸数字 0</b> 而不是 {@code AsyncTaskStatusEnum.PENDING.code()}：
 * 若引用常量，有人把 PENDING 的 code 改成 6 也不会有任何一处变红。</p>
 */
class AsyncTaskConverterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0, 0);

    @Test
    @DisplayName("新任务的初值：status = PENDING(0)、attempts = 0，且不由调用方决定")
    void draftCarriesInitialStatus() {
        AsyncTaskEntity entity = AsyncTaskConverter.toEntity(draft());

        assertEquals(0, entity.getStatus(),
                "新任务必须落成 PENDING：让调用方能指定状态，就等于允许一条新任务直接建成终态");
        assertEquals(0, entity.getAttempts());
        assertEquals(0, entity.getDeliveryAttempts());
        assertEquals(1, entity.getDeliveryStatus(),
                "有 callback_url 的新任务必须落成「待回调」(1)，否则终态时不会被武装投递");
    }

    @Test
    @DisplayName("没有 callback_url 的新任务：交付维度落成「无需回调」(0)，永不进入扫描集")
    void draftWithoutCallbackIsNotDeliverable() {
        AsyncTaskEntity entity = AsyncTaskConverter.toEntity(draft(null));

        assertEquals(0, entity.getDeliveryStatus());
        assertNull(entity.getCallbackUrl());
    }

    @Test
    @DisplayName("草稿字段逐个落进实体：漏一个就是「读出来恒为 null」")
    void draftCopiesEveryField() {
        AsyncTaskEntity entity = AsyncTaskConverter.toEntity(draft());

        assertEquals("tc-1", entity.getTaskCode());
        assertEquals("KLING_TASK", entity.getProfileCode());
        assertEquals("VIDEO_GENERATIONS", entity.getProtocol());
        assertEquals("up-1", entity.getUpstreamTaskId());
        assertEquals(NOW.plusSeconds(5), entity.getNextPollAt());
        assertEquals("{\"prompt\":\"a\"}", entity.getRequestJson());
        assertEquals("{}", entity.getPollSpecJson());
        assertEquals("{\"ruleCode\":\"r\"}", entity.getContextJson());
        assertEquals("RULE-1", entity.getBillingRuleCode());
        assertEquals("V1", entity.getBillingRuleVersionCode());
        assertEquals(NOW, entity.getSubmittedAt());
        assertEquals(NOW.plusMinutes(30), entity.getExpiresAt());
        assertEquals(NOW.plusDays(7), entity.getResultExpiresAt());
        assertEquals("https://client.example/callback", entity.getCallbackUrl());
    }

    @Test
    @DisplayName("实体 → 记录：字段逐个对齐，null 不会被兜底成别的值")
    void recordCopiesEveryField() {
        AsyncTaskEntity entity = AsyncTaskConverter.toEntity(draft());
        entity.setId(7L);
        entity.setClaimId("claim-1");
        entity.setResultJson("{\"result_url\":\"https://x/a.mp4\"}");
        entity.setErrorCode("UPSTREAM_TASK_FAILED");
        entity.setErrorMessage("prompt rejected");
        entity.setCompletedAt(NOW.plusMinutes(1));
        entity.setDeliveryStatus(2);
        entity.setDeliveryAttempts(1);
        entity.setNextDeliveryAt(NOW.plusMinutes(2));
        entity.setDeliveredAt(NOW.plusMinutes(1));
        entity.setDeliveryError("connection reset");

        AsyncTaskDto record = AsyncTaskConverter.toRecord(entity);

        assertEquals(7L, record.id());
        assertEquals("tc-1", record.taskCode());
        assertEquals("KLING_TASK", record.profileCode());
        assertEquals("up-1", record.upstreamTaskId());
        assertEquals(0, record.status());
        assertEquals(0, record.attempts());
        assertEquals("claim-1", record.claimId());
        assertEquals("{\"result_url\":\"https://x/a.mp4\"}", record.resultJson());
        assertEquals("UPSTREAM_TASK_FAILED", record.errorCode());
        assertEquals("prompt rejected", record.errorMessage());
        assertEquals("RULE-1", record.billingRuleCode());
        assertEquals("V1", record.billingRuleVersionCode());
        assertEquals(NOW.plusMinutes(1), record.completedAt());
        assertEquals(NOW.plusDays(7), record.resultExpiresAt());
        assertEquals("https://client.example/callback", record.callbackUrl());
        assertEquals(2, record.deliveryStatus());
        assertEquals(1, record.deliveryAttempts());
        assertEquals(NOW.plusMinutes(2), record.nextDeliveryAt());
        assertEquals(NOW.plusMinutes(1), record.deliveredAt());
        assertEquals("connection reset", record.deliveryError());
    }

    @Test
    @DisplayName("null 进 null 出：不做兜底（兜底会让「查不到」伪装成「查到空任务」）")
    void nullInNullOut() {
        assertNull(AsyncTaskConverter.toRecord(null));
    }

    private static AsyncTaskDraftDto draft() {
        return draft("https://client.example/callback");
    }

    private static AsyncTaskDraftDto draft(String callbackUrl) {
        return new AsyncTaskDraftDto("tc-1", "KLING_TASK", "VIDEO_GENERATIONS", "up-1",
                NOW.plusSeconds(5),
                "{\"prompt\":\"a\"}", "{}", "{\"ruleCode\":\"r\"}",
                "RULE-1", "V1",
                NOW, NOW.plusMinutes(30), NOW.plusDays(7),
                callbackUrl);
    }
}
