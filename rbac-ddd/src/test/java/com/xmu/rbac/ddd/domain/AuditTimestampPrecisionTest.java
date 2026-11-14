package com.xmu.rbac.ddd.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/** 审计响应与 TIMESTAMP(3) 保存值保持同一精度，防止精确时间窗口漏查。 */
class AuditTimestampPrecisionTest {
    @Test
    void canonicalizesNewAndRestoredFactsToDatabaseMilliseconds() {
        for (String time :
                List.of(
                        "2026-01-02T03:04:05.123456789Z",
                        "2026-01-02T03:04:05.123999999Z",
                        "2026-01-02T03:04:05.123Z")) {
            AuditFact fact =
                    new AuditFact(
                            null,
                            "2d950075-b011-439d-beba-d8a84418a149",
                            "course-project-management",
                            "COURSE",
                            1L,
                            "rbac-u1",
                            "Fixture",
                            List.of("teacher"),
                            "score_changed",
                            "student",
                            "cpm-student-1",
                            "Fixture target",
                            "{}",
                            "{}",
                            "SUCCESS",
                            null,
                            "fixture-trace",
                            "a".repeat(64),
                            Instant.parse(time));
            assertThat(fact.at()).isEqualTo(Instant.parse("2026-01-02T03:04:05.123Z"));
        }
    }
}
