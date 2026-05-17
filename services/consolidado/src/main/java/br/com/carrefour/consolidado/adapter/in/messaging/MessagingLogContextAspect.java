package br.com.carrefour.consolidado.adapter.in.messaging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Popula e limpa o MDC automaticamente para todos os @RabbitListener.
 * Elimina try/finally de contexto de log nos consumers.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MessagingLogContextAspect {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Around("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Map<String, String> anterior = MDC.getCopyOfContextMap();
        try {
            enrichMdc(pjp.getArgs());
            return pjp.proceed();
        } finally {
            if (anterior != null) MDC.setContextMap(anterior);
            else MDC.clear();
        }
    }

    private void enrichMdc(Object[] args) {
        for (var arg : args) {
            if (arg instanceof Message msg) {
                var correlationId = Optional
                        .ofNullable(msg.getMessageProperties().<String>getHeader(CORRELATION_HEADER))
                        .orElseGet(() -> UUID.randomUUID().toString());
                MDC.put("correlation_id", correlationId);
            }
        }
    }
}
