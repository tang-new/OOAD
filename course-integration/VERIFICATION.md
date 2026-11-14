> Latest dependency-refresh rerun: **2026-11-14T19:45:45.727Z**, current canonical structured6 + DDD6 = **12/12 PASS** through the real gateway and unchanged course jar. Both course DBs persisted score **88** and **1** approved membership; scoped audit GET confirmed synchronous **70→88** events; all owned child/database/Redis/private-file cleanup passed. Report: `C:\Users\vintces\AppData\Local\Temp\m6-course-real-hEBM62\report.json`; console: `C:/Users/vintces/Downloads/m21-course-real-current.log`. Full evidence: `REAL-CENTERS-VERIFICATION.md`. This is not the old final47 run; older image IDs below are historical.
>
> Executed private snapshot SHA256 (equal to canonical source SHA256): structured `5ee6f47dbb0c7fa49be870f2c63e192a69321e1fd225f7790f47f7c6820f299d`; DDD `b5e48780b750ccca7631b05bcd4cab2c3e97a661b9a342b8dde8e5290824da49`; gateway `1e2a7af05e76e9bed6e90a57381ccdeca727fac1d683ccac3afa4b2f0f2f8039`; unchanged course `464348b8f79ad4fc56ae3f201151ae4bdff65e4301ab05b43dacd8f283bf1f1a`.
>
> Dependency refresh: removed unused RBAC `poi-ooxml` dependency/management; course real-test `mysql2` 3.14.5→**3.23.1**. This task ran only the real two-center helper (no source changes, rebuild/clean, commit or upstream 440-test rerun). The retained MIT/upstream **440** domain-test evidence below is distinct from this latest **12**-scenario acceptance; the cross-database audit-unconfirmed window remains unchanged.

# Verified locally

Logs are outside Git at `C:/Users/vintces/Downloads/m6-course-*.log`.

- Fixed source HEAD: `fde08d5dd417abc543145aff33ca8378deb9ca36`; read-only reference `git status --short` remained empty.
- `tools/prepare-course.sh` reproduced into a second external cache, then `tests/verify-prepared.py`: PASS commit, MIT, no spreadsheet/env export, overlay, all **80** exact pinned operation routes.
- JDK21 + actual installed teacher CloneFactory + upstream Boot4.1.2/Lombok1.18.46 compiled. No root POM or RBAC service changes.
- `tests/run-domain-tests.sh`: **440 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Final log: `m6-course-domain-tests-final.log`. Original tests are all retained; two fixture-only identity corrections are isolated in patch006.
- `tests/http-boundary.mjs`: PASS **structured and DDD**, each using the real executable, real MySQL/JPA, real course services, and six actual API scenario classes. Only the central HTTP boundary is simulated; this is not a claim of real-center deployment acceptance.
- Boundary verification includes JWT signature/issuer/audience/expiry, server-owned route mapping, denied operation not entering a business mutation, forged JWT authority rejection, real Teacher/Student sub mapping, leader/no-CC versus nonleader/CC, service-key overwrite, synchronous timeout retry with identical UUID/body, scoped audit GET, and explicit committed-domain/audit-unconfirmed failure.
- `npm ci --registry=https://registry.npmjs.org && npm run build`: PASS. Real login/roles/course API client, base `/course/`.
- Docker backend image built: `ooad/course-backend:m6`, ID `sha256:04fac9abb80020fc79fe04ac62ff248b7866b68a2ed951654229ca7fb394f3bd`.
- Docker frontend image built: `ooad/course-frontend:m6`, ID `sha256:c1cca4372b81b2b74c27656e0dd124f77fd387b0f6974dcb34981ad6d1666510`.
- Actual frontend container HTTP `/course/` and compiled `/course/assets/*.js`: PASS200. Temporary HTTP-check container stopped/removed.
- Backend runtime contains actual jar + retained MIT notice, no `.xlsx`: PASS.
- `docker stack config -c course-integration/swarm-course.yaml`: PASS. No backend port published.
- `node --check` for both API scripts, `bash -n` for prepare/build/test scripts: PASS.

## Explicit handoff boundaries

The isolated MySQL container `m6-course-mysql` remains available at loopback13316. It owns only newly created `course_integration` and `course_integration_boundary` databases; no old DB was used. The two built images and prepared backend jar are available locally. No source commit or push was made by this delegated task.

Formal isolated Swarm deployment, real two-center acceptance and the main repository commit/push belong to the delegator. Use `tests/live-six-api.mjs` with real central tokens once its dedicated synthetic fixture has been provisioned. Preserve the documented cross-database unconfirmed audit window; do not describe this as atomic or whole-business-request idempotency.
