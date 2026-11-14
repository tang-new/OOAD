# Current canonical real two-center + gateway + teacher-course verification

Completed: **2026-11-14T18:08:00.936Z**. **12/12 scenarios PASS** (structured6 + DDD6).

**This final run uses the current default canonical target jars. It does not use the old final47 package or the old version-suffixed gateway jar.** No source code, runner, overlay, other module, Swarm service or volume was changed in this final rerun. Only this verification documentation was updated.

Full machine-readable report: `C:\Users\vintces\AppData\Local\Temp\m6-course-real-KuOhSn\report.json`
Console log: `C:/Users/vintces/Downloads/m18-course-real-current.log`

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
  - source SHA256: `9ad38452757e500c6534f75b57c25e708c8fd6dec3443994ef1b780aa10b0a05`
  - executed candidate SHA256: `9ad38452757e500c6534f75b57c25e708c8fd6dec3443994ef1b780aa10b0a05`
- **current canonical DDD**: `D:\Scripts\OOAD\rbac-ddd\target\rbac-ddd.jar`
  - source SHA256: `f5f64bac7f4676c2378103104811b3974e9ac1b80458ca5ba10bd50361745b38`
  - executed candidate SHA256: `f5f64bac7f4676c2378103104811b3974e9ac1b80458ca5ba10bd50361745b38`
- **current gateway (default rbac-gateway.jar)**: `D:\Scripts\OOAD\rbac-gateway\target\rbac-gateway.jar`
  - source SHA256: `8d41dd15deb529de64fb654b874ea3f91b634ebf3609d97cd7cda1628ee6125c`
  - executed candidate SHA256: `8d41dd15deb529de64fb654b874ea3f91b634ebf3609d97cd7cda1628ee6125c`
- **teacher course (unchanged latest overlay)**: `D:\Scripts\OOAD\.cache\course-source\prepared\backend\target\course-project-backend-0.0.1-SNAPSHOT.jar`
  - source SHA256: `464348b8f79ad4fc56ae3f201151ae4bdff65e4301ab05b43dacd8f283bf1f1a`
  - executed candidate SHA256: `464348b8f79ad4fc56ae3f201151ae4bdff65e4301ab05b43dacd8f283bf1f1a`

## Real identity, authorization, database and audit evidence
No central stub, fake provider or fabricated JWT is involved. The actual center HTTP APIs create synthetic departments/accounts/roles/grants, perform first-login password changes, and sign real user tokens. Every six-class business request goes through the real gateway and real teacher executable/JPA.

Subjects come from the actual center-created accounts and are mapped into only the new dedicated course database. They are not hard-coded:
- **structured** leader: `rbac-22d8cd20-3ca1-4179-9c8e-9123c4885ad2` → Teacher `1`; student leader: `rbac-1f358fa9-0d1b-4416-9b86-9e7ff46c1099` → Student `610001`.
  - Actual course DB score: **88**; approved persisted group memberships: **1**.
  - Actual external audit POST, read back through scoped course GET: **before70 → after88**; audit ID `52`, requestId `1a5a4e5e-b72b-4a29-9a7e-7bbe845b74b9`.
- **ddd** leader: `rbac-abb0c3a0-b247-40da-a9d1-ac37c4a3efe4` → Teacher `1`; student leader: `rbac-8c10803e-cad2-468f-9e3a-f0ea1845315b` → Student `610001`.
  - Actual course DB score: **88**; approved persisted group memberships: **1**.
  - Actual external audit POST, read back through scoped course GET: **before70 → after88**; audit ID `51`, requestId `83610155-e9ef-4393-86d7-a8ea551768e4`.

For both centers the real authorities endpoint confirms all three teacher sessions have teacher identity and coarse copy-operation allowed=true; only CC_TEACHER has CC. The four real copy outcomes above therefore prove teacher qualification is not CC and operation admission is not fine-grained CC.

## Isolation and cleanup
Explicit JDK21; whitelist-only child environment; canonical SPRING_APPLICATION_JSON pins datasource, Flyway, Redis, JWT files and audit key. All JVMs use `--spring.config.location=classpath:/`; all fetch calls reject redirects. Ports are independently allocated loopback ports. Only newly generated center13306/course13316 database names pass the destructive-operation allowlist. No old DB, Swarm service or volume was touched.

- **structured** center DB: `m6_course_real_center_structured_1794679444791_dbaf2443`; course DB: `m6_course_real_course_structured_1794679444791_dbaf2443`.
  - Own child JVMs stopped and awaited: **PASS**; both own DBs dropped and absence verified: **PASS**; own Redis namespace removed: **PASS**; private copied jars/keys removed: **PASS**.
- **ddd** center DB: `m6_course_real_center_ddd_1794679540701_3aef482a`; course DB: `m6_course_real_course_ddd_1794679540701_3aef482a`.
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

This proves the current canonical immutable jar combination. It does not claim deployment/image verification or rerun unrelated backend JUnit/coverage gates. No remaining failure or cross-module code fix was found in this requested final real-course rerun. No commit or push was made.
