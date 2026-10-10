package org.chobit.knot.gateway.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.constants.enums.OperationLogStatusEnum;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.service.OperationLogService;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/**
 * 操作日志切面：拦截 {@link OperationLog} 注解，并异步写入操作日志表。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final Set<String> IGNORED_SNAPSHOT_FIELDS = Set.of(
            "updatedAt", "updated_at", "updateTime", "update_time");

    private final OperationLogService operationLogService;
    private final BeanFactory beanFactory;
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Constructs a new instance.
     */
    public OperationLogAspect(OperationLogService operationLogService, BeanFactory beanFactory) {
        this.operationLogService = operationLogService;
        this.beanFactory = beanFactory;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Around("@annotation(operationLog)")
    public Object recordLog(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        OperationLogEntity logEntity = buildLogEntity(joinPoint, operationLog);
        captureOldValue(joinPoint, operationLog, logEntity);

        try {
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            logEntity.setStatus(OperationLogStatusEnum.SUCCESS.code());
            logEntity.setExecutionTime(executionTime);
            applyAfterResult(joinPoint, operationLog, result, logEntity);
            captureNewValue(joinPoint, operationLog, result, logEntity);
            // 无实质变化的更新操作（前后快照完全一致）不记录日志，避免噪声审计
            if (isNoChangeUpdate(operationLog, logEntity)) {
                return result;
            }
            asyncSaveLog(logEntity);
            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logEntity.setStatus(OperationLogStatusEnum.FAILURE.code());
            logEntity.setExecutionTime(executionTime);
            logEntity.setErrorMsg(e.getMessage());
            asyncSaveLog(logEntity);
            throw e;
        }
    }

    private void captureOldValue(ProceedingJoinPoint joinPoint, OperationLog operationLog, OperationLogEntity logEntity) {
        if (!operationLog.recordOldValue() || operationLog.oldValueSpel().isBlank()) {
            return;
        }
        try {
            Object oldObj = evaluateExpression(joinPoint, operationLog.oldValueSpel(), null);
            logEntity.setOldValue(toJson(oldObj));
        } catch (Exception e) {
            log.warn("Operation log oldValue SpEL failed: {}", operationLog.oldValueSpel(), e);
        }
    }

    private void captureNewValue(ProceedingJoinPoint joinPoint, OperationLog operationLog, Object result, OperationLogEntity logEntity) {
        if (!operationLog.recordNewValue()) {
            return;
        }
        try {
            Object newObj;
            if (!operationLog.newValueSpel().isBlank()) {
                newObj = evaluateExpression(joinPoint, operationLog.newValueSpel(), result);
            } else {
                newObj = result;
            }
            logEntity.setNewValue(toJson(newObj));
        } catch (Exception e) {
            log.warn("Operation log newValue failed", e);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        String json = JsonKit.toJson(value);
        return json != null ? removeIgnoredSnapshotFields(json) : String.valueOf(value);
    }

    private String removeIgnoredSnapshotFields(String json) {
        JsonNode root = JsonKit.parse(json);
        if (root == null) {
            return json;
        }
        removeIgnoredSnapshotFields(root);
        return JsonKit.toJson(root);
    }

    private void removeIgnoredSnapshotFields(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            IGNORED_SNAPSHOT_FIELDS.forEach(objectNode::remove);
            objectNode.elements().forEachRemaining(this::removeIgnoredSnapshotFields);
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.elements().forEachRemaining(this::removeIgnoredSnapshotFields);
        }
    }

    private Object evaluateExpression(ProceedingJoinPoint joinPoint, String expression, Object result) {
        StandardEvaluationContext context = buildContext(joinPoint, result);
        return parser.parseExpression(expression).getValue(context);
    }

    private void applyAfterResult(ProceedingJoinPoint joinPoint, OperationLog operationLog,
                                  Object result, OperationLogEntity logEntity) {
        if (!operationLog.entityIdAfter().isBlank()) {
            Long id = parseSpelLong(joinPoint, operationLog.entityIdAfter(), result);
            if (id != null) {
                logEntity.setEntityId(id);
            }
        }
        if (!operationLog.entityNameAfter().isBlank()) {
            String name = parseSpel(joinPoint, operationLog.entityNameAfter(), result);
            if (name != null && !name.isBlank()) {
                logEntity.setEntityName(name);
            }
        }
    }

    private OperationLogEntity buildLogEntity(ProceedingJoinPoint joinPoint, OperationLog operationLog) {
        OperationLogEntity logEntity = new OperationLogEntity();
        logEntity.setModule(operationLog.module());
        logEntity.setOperation(operationLog.operation());
        logEntity.setEntityType(parseSpel(joinPoint, operationLog.entityType(), null));
        logEntity.setEntityId(parseSpelLong(joinPoint, operationLog.entityId(), null));
        logEntity.setEntityName(parseSpel(joinPoint, operationLog.entityName(), null));
        logEntity.setDescription(parseSpel(joinPoint, operationLog.description(), null));

        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            logEntity.setOperatorId(parseLong(request.getAttribute("userId")));
            logEntity.setOperatorName(parseString(request.getAttribute("username")));
            logEntity.setIpAddress(getClientIp(request));
            logEntity.setUserAgent(request.getHeader("User-Agent"));
        }
        return logEntity;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private void asyncSaveLog(OperationLogEntity logEntity) {
        operationLogService.saveAsync(logEntity);
    }

    /**
     * 判定是否为「无变化的更新操作」：仅当操作类型为 UPDATE 且旧值、新值均已捕获且完全一致时成立。
     * 用于过滤掉前端重复提交、或字段值未真正改变的保存请求。
     */
    private boolean isNoChangeUpdate(OperationLog operationLog, OperationLogEntity logEntity) {
        if (!"UPDATE".equals(operationLog.operation())) {
            return false;
        }
        String oldValue = logEntity.getOldValue();
        String newValue = logEntity.getNewValue();
        return oldValue != null && oldValue.equals(newValue);
    }

    private StandardEvaluationContext buildContext(ProceedingJoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        int n = Math.max(parameterNames != null ? parameterNames.length : 0, args != null ? args.length : 0);
        for (int i = 0; i < n; i++) {
            Object arg = args != null && i < args.length ? args[i] : null;
            context.setVariable("p" + i, arg);
            if (parameterNames != null && i < parameterNames.length && parameterNames[i] != null) {
                context.setVariable(parameterNames[i], arg);
            }
        }
        if (result != null) {
            context.setVariable("result", result);
        }
        return context;
    }

    private String parseSpel(ProceedingJoinPoint joinPoint, String expression, Object result) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        try {
            StandardEvaluationContext context = buildContext(joinPoint, result);
            return parser.parseExpression(expression).getValue(context, String.class);
        } catch (Exception e) {
            return expression;
        }
    }

    private Long parseSpelLong(ProceedingJoinPoint joinPoint, String expression, Object result) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        try {
            StandardEvaluationContext context = buildContext(joinPoint, result);
            Object value = parser.parseExpression(expression).getValue(context);
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
