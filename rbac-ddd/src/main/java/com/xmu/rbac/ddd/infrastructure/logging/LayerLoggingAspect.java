package com.xmu.rbac.ddd.infrastructure.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 三层技术日志只打印固定元数据，绝不读取参数、返回对象或异常消息。 */
@Aspect
@Component
public class LayerLoggingAspect {
    private static final Logger LOG = LoggerFactory.getLogger(LayerLoggingAspect.class);

    @Around(
            "execution(public * com.xmu.rbac.ddd.interfaces..*(..)) || execution(public *"
                    + " com.xmu.rbac.ddd.application..*(..)) || execution(public *"
                    + " com.xmu.rbac.ddd.infrastructure.persistence..*(..)) || execution(public *"
                    + " com.xmu.rbac.ddd.infrastructure.identity..*(..)) || execution(public *"
                    + " com.xmu.rbac.ddd.infrastructure.cache..*(..))")
    public Object trace(ProceedingJoinPoint call) throws Throwable {
        boolean owner = MDC.get("traceId") == null;
        if (owner) MDC.put("traceId", UUID.randomUUID().toString());
        long started = System.nanoTime();
        String className = call.getSignature().getDeclaringTypeName();
        String layer =
                className.contains(".application.")
                        ? "application"
                        : className.contains(".interfaces.") ? "api" : "infrastructure";
        String result = "SUCCESS";
        String exception = "none";
        try {
            return call.proceed();
        } catch (Throwable failure) {
            result = "FAILURE";
            exception = failure.getClass().getSimpleName();
            throw failure;
        } finally {
            LOG.info(
                    "traceId={} layer={} class={} method={} durationMs={} result={}"
                            + " exceptionType={}",
                    MDC.get("traceId"),
                    layer,
                    className,
                    call.getSignature().getName(),
                    (System.nanoTime() - started) / 1000000,
                    result,
                    exception);
            if (owner) MDC.remove("traceId");
        }
    }
}
