# Real two-center + gateway + teacher-course verification

Completed: 2026-11-14T16:21:22.133Z. Overall PASS. No stub/provider/JWT fabrication.

External full report: `C:\Users\vintces\AppData\Local\Temp\m6-course-real-WUDc3F\report.json`
External console log: `C:/Users/vintces/Downloads/m6-course-real-final47-canonical-ddd.log`

| Business scenario | structured final47 | canonical DDD |
|---|---|---|
| term-management | PASS | PASS |
| teacher-course-submission | PASS | PASS |
| independent-course-review | PASS | PASS |
| copy-leader-CC-and-teacher-qualification | PASS | PASS |
| student-leader-join-approval | PASS | PASS |
| oral-score-audit-POST-GET | PASS | PASS |

## Exact executed bytes
Each source jar was copied into a private temporary directory, hash-checked before/after copying, then executed from that copy. Source and candidate SHA256 match.

- structured final47: `D:\m3-structured-final47.jar`
  - source/candidate SHA256: `e4a1542c6df02eb549fe4c171272af36c6a538cc72b27e39877a17a00b6c1207`
- canonical DDD: `D:\Scripts\OOAD\rbac-ddd\target\rbac-ddd.jar`
  - source/candidate SHA256: `1b410d6b5a31e211d37aec7306a00a41424b49d3fc7899bfb960c6a8cb42a650`
- gateway: `D:\Scripts\OOAD\rbac-gateway\target\rbac-gateway-0.1.0-SNAPSHOT.jar`
  - source/candidate SHA256: `2b53fd485a1df40c95fa5ce1dcf884a7343bb224a0566ed610e977c1345f2f1c`
- patched teacher course: `D:\Scripts\OOAD\.cache\course-source\prepared\backend\target\course-project-backend-0.0.1-SNAPSHOT.jar`
  - source/candidate SHA256: `464348b8f79ad4fc56ae3f201151ae4bdff65e4301ab05b43dacd8f283bf1f1a`

## Actual evidence
- Real center HTTP creates synthetic accounts, custom roles, API grants and department eligibility; actual `/auth/login` signs the user bearers. First-login password changes are performed through HTTP. No token is manually signed.
- Real `/integration/authorities` confirms all three teacher users pass coarse copy operation; only the CC account has CC. Leader without CC returns201, unrelated teacher without CC returns403, nonteacher admin returns403.
- Real source subjects are written into the new course fixture and resolved to actual local Teacher/Student records. Every business request goes through the real gateway.
- Both databases confirm score88 and one actual approved group membership. Course audit GET finds the real center POST event with before70/after88.
- Prior real run exposed DDD HTTP400 AUDIT_UNSAFE_FIELD for extra operationId/result JSON keys, yielding course503 AUDIT_UNCONFIRMED. The course overlay now sends the common flat safe-field schema; target retains the operation name. Latest specified jars pass without backend changes.

## Isolation and cleanup
- Explicit JDK21, whitelist-only child environment, canonical SPRING_APPLICATION_JSON pins datasource/Flyway/Redis/key files/audit key; `--spring.config.location=classpath:/`. Every fetch rejects redirects.
- New unique databases only: center loopback13306; course loopback13316. No Swarm service/volume or old database was changed.
- Center readiness probe waits until ApplicationRunners finish; source structured policy ddl-auto=none is preserved, DDD/course validate schemas.

- structured: `m6_course_real_center_structured_1794672935601_eb6a7ef3` and `m6_course_real_course_structured_1794672935601_eb6a7ef3` dropped and absence verified; own children exited, own Redis namespace cleared, private jars/keys deleted: **{'children': True, 'databases': True, 'redis': True, 'secrets': True}**.
- ddd: `m6_course_real_center_ddd_1794673103772_0a35ca82` and `m6_course_real_course_ddd_1794673103772_0a35ca82` dropped and absence verified; own children exited, own Redis namespace cleared, private jars/keys deleted: **{'children': True, 'databases': True, 'redis': True, 'secrets': True}**.

## Reproduce
```bash
cd course-integration/tests
npm ci
export JAVA_HOME="D:/Software/Eclipse Adoptium/jdk-21.0.7.6-hotspot"
# Supply REAL_CENTER_DB_PASSWORD and REAL_COURSE_DB_PASSWORD without printing them.
REAL_STRUCTURED_JAR="D:/m3-structured-final47.jar" node run-real-centers.mjs --module=both
```

No commit/push was made. This verifies immutable jar candidates, not the currently deployed Swarm images. Rebuilding the course image from the new jar and formal candidate Swarm acceptance remain with the deployment owner.
