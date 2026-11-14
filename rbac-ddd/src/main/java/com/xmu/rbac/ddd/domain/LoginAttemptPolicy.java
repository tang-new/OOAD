package com.xmu.rbac.ddd.domain;

import java.time.Instant;

/** 失败五次锁定九百秒，已过期锁重新计数；只有正确凭据成功才清零。 */
public final class LoginAttemptPolicy {
    public record Result(boolean accepted, int failedCount, Instant lockedUntil) {}

    public boolean locked(Instant until, Instant now) {
        return until != null && until.isAfter(now);
    }

    public Result evaluate(
            int failedCount,
            Instant until,
            boolean active,
            boolean credentialMatches,
            Instant now) {
        if (locked(until, now)) return new Result(false, failedCount, until);
        int count = until == null ? failedCount : 0;
        if (!active) return new Result(false, count, null);
        if (credentialMatches) return new Result(true, 0, null);
        count++;
        return new Result(false, count, count >= 5 ? now.plusSeconds(900) : null);
    }
}
