package com.xmu.rbac.structured.config;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
/** 三层技术日志只含调用元数据，不记录参数、返回对象或异常消息。 */
@Aspect
@Component
public class LayerLoggingAspect {
    private static final Logger LOG = LoggerFactory.getLogger(LayerLoggingAspect.class);
    @Around("execution(public * com.xmu.rbac.structured.controller..*(..)) || execution(public * "
        + "com.xmu.rbac.structured.service..*(..)) || execution(public * "
        + "com.xmu.rbac.structured.mapper..*(..))")
    public Object
    log(ProceedingJoinPoint invocation) throws Throwable {
        long started = System.nanoTime();
        String result = "SUCCESS", exceptionType = "-";
        try {
            return invocation.proceed();
        } catch (Throwable failure) {
            result = "FAILURE";
            exceptionType = failure.getClass().getSimpleName();
            throw failure;
        } finally {
            String name = invocation.getSignature().getDeclaringTypeName();
            String layer = name.contains(".controller.") ? "API"
                : name.contains(".service.")             ? "SERVICE"
                                                         : "INFRASTRUCTURE";
            LOG.info(
                "traceId={} layer={} class={} method={} durationMs={} result={} exceptionType={}",
                MDC.get("traceId"), layer, name, invocation.getSignature().getName(),
                (System.nanoTime() - started) / 1000000, result, exceptionType);
        }
    }
}
