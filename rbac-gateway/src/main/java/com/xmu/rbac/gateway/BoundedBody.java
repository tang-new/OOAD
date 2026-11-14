package com.xmu.rbac.gateway;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** 所有字节读取都有硬上限，上传文件按原字节传递而不解析或记录。 */
public final class BoundedBody {
    private BoundedBody() { }
    /** 使用虚拟线程限制响应体读取时长，超时关闭流而不是留下悬挂读取。 */
    public static byte[] readTimed(InputStream input, int limit, int timeoutMs) throws IOException {
        try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Future<byte[]> future = executor.submit(() -> read(input, limit, 503));
            try {
                return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                input.close();
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new GatewayFailure(503, "UPSTREAM_UNAVAILABLE");
            } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException exception) {
                input.close();
                future.cancel(true);
                throw new GatewayFailure(503, "UPSTREAM_UNAVAILABLE");
            }
        }
    }

    public static byte[] read(InputStream input, int limit, int status) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer, 0, Math.min(buffer.length, limit - total + 1))) != -1) {
            total += count;
            if (total > limit) { throw new GatewayFailure(status, "BODY_LIMIT_EXCEEDED"); }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
