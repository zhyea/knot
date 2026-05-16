package org.chobit.knot.gateway.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 操作日志切面：拦截 {@link OperationLog} 注解，异步写入 operation_logs。
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final BeanFactory beanFactory;
    private final ExpressionParser parser = new SpelExpressionParser();

    public OperationLogAspect(OperationLogService operationLogService, ObjectMapper objectMapper, BeanFactory beanFactory) {
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
        this.beanFactory = beanFactory;
    }

    @Around("@annotation(operationLog)")
    public Object recordLog(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        OperationLogEntity logEntity = buildLogEntity(joinPoint, operationLog);
        captureOldValue(joinPoint, operationLog, logEntity);

        try {
            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            logEntity.setStatus("SUCCESS");
            logEntity.setExecutionTime(executionTime);
            applyAfterResult(joinPoint, operationLog, result, logEntity);
            captureNewValue(joinPoint, operationLog, result, logEntity);
            asyncSaveLog(logEntity);
            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logEntity.setStatus("FAILURE");
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
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Operation log JSON serialize failed", e);
            return String.valueOf(value);
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
            logEntity.setIpAddress(getClientIp(request));
            logEntity.setUserAgent(request.getHeader("User-Agent"));
        }
        return logEntity;
    }

    private void asyncSaveLog(OperationLogEntity logEntity) {
        operationLogService.saveAsync(logEntity);
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
