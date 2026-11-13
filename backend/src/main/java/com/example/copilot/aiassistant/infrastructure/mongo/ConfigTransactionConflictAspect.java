package com.example.copilot.aiassistant.infrastructure.mongo;

import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Turns a Mongo conflict that surfaces only when the use case's transaction commits (thrown by the transaction
 * interceptor, after the repository's own mapping had its chance) into {@code AI_CONFIG_CONFLICT}. Ordered first
 * so it wraps the transaction interceptor. The original failure is kept as the cause.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConfigTransactionConflictAspect {

    @Around("execution(public * com.example.copilot.aiassistant.application.command.config.ConfigCommandUseCase.*(..))")
    public Object translateConflicts(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException e) {
            if (!(e instanceof AssistantException) && MongoConflicts.isConflict(e)) {
                AssistantException conflict = new AssistantException(ErrorCode.AI_CONFIG_CONFLICT);
                conflict.initCause(e);
                throw conflict;
            }
            throw e;
        }
    }
}
