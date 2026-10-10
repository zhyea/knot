package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数值状态枚举的统一契约测试。
 *
 * <p>新增 {@link NumericEnumOption} 实现时，**必须**把类加进 {@link #NUMERIC_ENUMS} ——
 * 本测试会强制校验每个枚举的 code 唯一性、label 非空、{@code fromCode / requireCode / codes}
 * 三件套齐备且行为一致。漏写三件套会在这里失败，而不是在生产环境出现 null 状态。</p>
 *
 * <p>重点守护 {@code 0}：二态枚举的关闭态是合法值，{@code fromCode(0)} 必须命中而非返回 null。</p>
 */
class NumericStatusEnumContractTest {

    private static final List<Class<?>> NUMERIC_ENUMS = List.of(
            EnabledStatusEnum.class,
            LogicalModelPublishStatusEnum.class,
            OperationLogStatusEnum.class,
            ScheduledTaskRunStatusEnum.class,
            PluginPackageStatusEnum.class,
            PluginInstanceStatusEnum.class,
            ExternalModelSyncStatusEnum.class,
            HealthStatusEnum.class,
            ReconciliationStatusEnum.class
    );

    private static Object[] valuesOf(Class<?> clazz) throws Exception {
        Method values = clazz.getMethod("values");
        return (Object[]) values.invoke(null);
    }

    @SuppressWarnings("unchecked")
    private static Object invokeStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = clazz.getMethod(name, paramTypes);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }

    @Test
    void everyNumericEnumHasUniqueCodesAndNonEmptyLabels() throws Exception {
        for (Class<?> clazz : NUMERIC_ENUMS) {
            Set<Integer> codes = new HashSet<>();
            for (Object item : valuesOf(clazz)) {
                NumericEnumOption option = (NumericEnumOption) item;
                assertTrue(codes.add(option.code()),
                        "duplicated code " + option.code() + " in " + clazz.getSimpleName());
                assertNotNull(option.label(), "null label in " + clazz.getSimpleName());
                assertFalse(option.label().isBlank(), "blank label in " + clazz.getSimpleName());
            }
        }
    }

    @Test
    void fromCodeResolvesEveryDeclaredValueIncludingZero() throws Exception {
        for (Class<?> clazz : NUMERIC_ENUMS) {
            for (Object item : valuesOf(clazz)) {
                NumericEnumOption option = (NumericEnumOption) item;
                Object resolved = invokeStatic(clazz, "fromCode", new Class<?>[]{Integer.class}, option.code());
                assertSame(option, resolved,
                        "fromCode(" + option.code() + ") failed for " + clazz.getSimpleName());
            }
        }
    }

    @Test
    void fromCodeReturnsNullForNullAndUnknownCode() throws Exception {
        for (Class<?> clazz : NUMERIC_ENUMS) {
            assertNull(invokeStatic(clazz, "fromCode", new Class<?>[]{Integer.class}, (Object) null),
                    "fromCode(null) should be null for " + clazz.getSimpleName());
            assertNull(invokeStatic(clazz, "fromCode", new Class<?>[]{Integer.class}, 9999),
                    "fromCode(9999) should be null for " + clazz.getSimpleName());
        }
    }

    @Test
    void requireCodeRejectsNullAndUnknownCode() {
        for (Class<?> clazz : NUMERIC_ENUMS) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> invokeStatic(clazz, "requireCode", new Class<?>[]{Integer.class, String.class}, null, "bad status"),
                    "requireCode(null) should be rejected for " + clazz.getSimpleName());
            assertNotNull(ex.getMessage());

            assertThrows(BusinessException.class,
                    () -> invokeStatic(clazz, "requireCode", new Class<?>[]{Integer.class, String.class}, 9999, "bad status"),
                    "requireCode(9999) should be rejected for " + clazz.getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void codesMatchesDeclaredValuesInOrder() throws Exception {
        for (Class<?> clazz : NUMERIC_ENUMS) {
            List<Integer> codes = (List<Integer>) invokeStatic(clazz, "codes", new Class<?>[]{});
            Object[] values = valuesOf(clazz);
            assertEquals(values.length, codes.size(), "codes size mismatch for " + clazz.getSimpleName());
            for (int i = 0; i < values.length; i++) {
                assertEquals(((NumericEnumOption) values[i]).code(), codes.get(i),
                        "codes order mismatch at " + i + " for " + clazz.getSimpleName());
            }
        }
    }

    // ==================== EnabledStatusEnum 专项（0 值语义） ====================

    @Test
    void enabledStatusUsesOneAndZero() {
        assertEquals(1, EnabledStatusEnum.ENABLED.code());
        assertEquals(0, EnabledStatusEnum.DISABLED.code());
        assertEquals(List.of(1, 0), EnabledStatusEnum.codes());
    }

    @Test
    void zeroIsAValidEnabledStatusCode() {
        assertSame(EnabledStatusEnum.DISABLED, EnabledStatusEnum.fromCode(0));
        assertSame(EnabledStatusEnum.DISABLED, EnabledStatusEnum.requireCode(0, "bad status"));
        assertEquals("禁用", EnabledStatusEnum.fromCode(0).label());
    }

    @Test
    void enabledStatusBooleanBridgeIsExplicit() {
        assertEquals(1, EnabledStatusEnum.codeOf(true));
        assertEquals(0, EnabledStatusEnum.codeOf(false));

        assertTrue(EnabledStatusEnum.isEnabled(1));
        assertFalse(EnabledStatusEnum.isEnabled(0));
        // null 不得被当成「启用」，也不得 NPE
        assertFalse(EnabledStatusEnum.isEnabled(null));
    }

    @Test
    void twoStateDomainsNeverExposeDeletedSemantics() {
        // 二态枚举不含删除语义；删除一律走 is_deleted
        assertEquals(2, EnabledStatusEnum.values().length);
        assertTrue(Arrays.stream(EnabledStatusEnum.values())
                .noneMatch(item -> "DELETED".equalsIgnoreCase(item.name())));
    }
}
