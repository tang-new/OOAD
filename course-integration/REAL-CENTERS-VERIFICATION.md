# Current canonical real two-center + gateway + teacher-course verification

Completed: **2026-11-14T19:45:45.727Z**. **12/12 scenarios PASS** (structured6 + DDD6).

**This final run uses the current default canonical target jars. It does not use the old final47 package or the old version-suffixed gateway jar.** No source code, runner, overlay, other module, Swarm service or volume was changed in this final rerun. Only this verification documentation was updated.

Full machine-readable report: `C:\Users\vintces\AppData\Local\Temp\m6-course-real-hEBM62\report.json`
Console log: `C:/Users/vintces/Downloads/m21-course-real-current.log`

Dependency-only refresh: unused `poi-ooxml` dependency/management removed from the RBAC build; real-course test client `mysql2` upgraded from 3.14.5 to **3.23.1** (installed version checked). Course Java and course jar are unchanged. This task only reran `node course-integration/tests/run-real-centers.mjs --module=both`; no rebuild/clean, npm install/audit, JUnit or upstream 440-test rerun was performed. Existing guard-test results below are historical. The upstream MIT provenance and prior **440** domain-test result remain separate from this **12**-scenario real-center acceptance.

## Six classes per real backend

| Business scenario | current structured | current DDD | actual HTTP |
|---|---|---|---|
| term-management | PASS | PASS | POST /terms 201 |
| teacher-course-submission | PASS | PASS | GET /me 200; POST course 201; POST submit-review 200 |
| independent-course-review | PASS | PASS | POST review 200 |
| copy-leader-CC-and-teacher-qualification | PASS | PASS | leader/noCC 201; nonleader/CC 201; nonleader/noCC 403; admin-not-teacher 403 |
| student-leader-join-approval | PASS | PASS | GET /me 200; PATCH join-request 200 |
| oral-score-audit-POST-GET | PASS | PASS | PATCH oral-record 200; GET audit-logs 200 |

## Exact current source / executed candidate hashes
The runner copies each real fat jar into a private temporary directory and verifies source-before/source-after/candidate SHA256 equality. JVMs execute the copy, never hold target jars open.

- **current structured**: `D:\Scripts\OOAD\rbac-structured\target\rbac-structured.jar`
  - source SHA256: `5ee6f47dbb0c7fa49be870f2c63e192a69321e1fd225f7790f47f7c6820f299d`
  - executed candidate SHA256: `5ee6f47dbb0c7fa49be870f2c63e192a69321e1fd225f7790f47f7c6820f299d`
- **current canonical DDD**: `D:\Scripts\OOAD\rbac-ddd\target\rbac-ddd.jar`
  - source SHA256: `b5e48780b750ccca7631b05bcd4cab2c3e97a661b9a342b8dde8e5290824da49`
  - executed candidate SHA256: `b5e48780b750ccca7631b05bcd4cab2c3e97a661b9a342b8dde8e5290824da49`
- **current gateway (default rbac-gateway.jar)**: `D:\Scripts\OOAD\rbac-gateway\target\rbac-gateway.jar`
  - source SHA256: `1e2a7af05e76e9bed6e90a57381ccdeca727fac1d683ccac3afa4b2f0f2f8039`
  - executed candidate SHA256: `1e2a7af05e76e9bed6e90a57381ccdeca727fac1d683ccac3afa4b2f0f2f8039`
- **teacher course (unchanged latest overlay)**: `D:\Scripts\OOAD\.cache\course-source\prepared\backend\target\course-project-backend-0.0.1-SNAPSHOT.jar`
  - source SHA256: `464348b8f79ad4fc56ae3f201151ae4bdff65e4301ab05b43dacd8f283bf1f1a`
  - executed candidate SHA256: `464348b8f79ad4fc56ae3f201151ae4bdff65e4301ab05b43dacd8f283bf1f1a`

## Real identity, authorization, database and audit evidence
No central stub, fake provider or fabricated JWT is involved. The actual center HTTP APIs create synthetic departments/accounts/roles/grants, perform first-login password changes, and sign real user tokens. Every six-class business request goes through the real gateway and real teacher executable/JPA.

Subjects come from the actual center-created accounts and are mapped into only the new dedicated course database. They are not hard-coded:
- **structured** leader: `rbac-1ec7e921-5b5b-4e63-a867-0a3b0db0e954` → Teacher `1`; student leader: `rbac-4b40da1b-4608-40a7-a880-4161552314ab` → Student `610001`.
  - Actual course DB score: **88**; approved persisted group memberships: **1**.
  - Actual external audit POST, read back through scoped course GET: **before70 → after88**; audit ID `52`, requestId `585ecedb-f0c3-4548-ac12-0ed832e08f42`.
- **ddd** leader: `rbac-8a709341-396f-4e96-9c62-536769219660` → Teacher `1`; student leader: `rbac-7217f54c-59f0-45ca-b449-f5ffc7351b00` → Student `610001`.
  - Actual course DB score: **88**; approved persisted group memberships: **1**.
  - Actual external audit POST, read back through scoped course GET: **before70 → after88**; audit ID `51`, requestId `7f73dba7-0aa9-4ebf-ba23-1a1e14e2e18d`.

For both centers the real authorities endpoint confirms all three teacher sessions have teacher identity and coarse copy-operation allowed=true; only CC_TEACHER has CC. The four real copy outcomes above therefore prove teacher qualification is not CC and operation admission is not fine-grained CC.

## Isolation and cleanup
Explicit JDK21; whitelist-only child environment; canonical SPRING_APPLICATION_JSON pins datasource, Flyway, Redis, JWT files and audit key. All JVMs use `--spring.config.location=classpath:/`; all fetch calls reject redirects. Ports are independently allocated loopback ports. Only newly generated center13306/course13316 database names pass the destructive-operation allowlist. No old DB, Swarm service or volume was touched.

- **structured** center DB: `m6_course_real_center_structured_1794685105766_a21a3d53`; course DB: `m6_course_real_course_structured_1794685105766_a21a3d53`.
  - Own child JVMs stopped and awaited: **PASS**; both own DBs dropped and absence verified: **PASS**; own Redis namespace removed: **PASS**; private copied jars/keys removed: **PASS**.
- **ddd** center DB: `m6_course_real_center_ddd_1794685293358_bf4b12a4`; course DB: `m6_course_real_course_ddd_1794685293358_bf4b12a4`.
  - Own child JVMs stopped and awaited: **PASS**; both own DBs dropped and absence verified: **PASS**; own Redis namespace removed: **PASS**; private copied jars/keys removed: **PASS**.

## Reproduction and existing guards
From the repository root, with explicit JDK21 JAVA_HOME and dedicated REAL_CENTER_DB_PASSWORD / REAL_COURSE_DB_PASSWORD supplied privately:
```bash
unset REAL_STRUCTURED_JAR REAL_DDD_JAR REAL_GATEWAY_JAR REAL_COURSE_JAR
node course-integration/tests/run-real-centers.mjs --module=both
```
Run `npm ci` in `course-integration/tests` first if its mysql2 dependency is not installed. No password or token is printed.

- `node --check` passed for run-real-centers.mjs, live-six-api.mjs, real-provision.mjs and real-environment.mjs.
- Existing course real-environment/fixture database guard tests: **2/2 PASS**.
- Existing acceptance database guard test: **PASS** (`m18-course-real-fixtureguard.log`); unrelated tests were not rerun by that focused command.

The synchronous external audit success here does not eliminate the cross-database committed-domain/audit-unconfirmed failure window; it is not atomic or whole-business-request idempotency.

This proves the current canonical immutable jar combination. It does not claim deployment/image verification or rerun unrelated backend JUnit/coverage gates. No remaining failure or cross-module code fix was found in this requested final real-course rerun. No commit or push was made.
