# Real teacher course integration

Latest real two-center/gateway/course verification and exact candidate hashes: [REAL-CENTERS-VERIFICATION.md](REAL-CENTERS-VERIFICATION.md).

## Source / ownership

Pinned MIT upstream: `https://gitee.com/mingqcn/course-project-mgt.git`, commit
`fde08d5dd417abc543145aff33ca8378deb9ca36`. Original MIT notice is
`LICENSE.upstream` and remains `LICENSE` in the prepared source/runtime image.
The reference checkout is read-only. The prepare command archives approved tracked
paths from the fixed commit, then applies reviewable patches and the overlay. It never
archives the root or copies `选课名单.xlsx`; it removes every exported `.xlsx` and `.env*`.
Only this directory and `tools/prepare-course.sh` belong to this integration task.

```bash
export JAVA_HOME='/d/Software/Eclipse Adoptium/jdk-21.0.7.6-hotspot'
export COURSE_SOURCE='/c/Users/vintces/Downloads/rbac1-context-extracted/course-project-mgt-reference'
# Omit COURSE_SOURCE to fetch the pinned Gitee commit reproducibly.
bash tools/install-clonefactory.sh # unchanged teacher processor; install once
bash tools/prepare-course.sh
bash course-integration/tests/run-domain-tests.sh
(cd course-integration/frontend && npm ci && npm run build)
bash course-integration/build-images.sh
```

The cache is `.cache/course-source/prepared`. Do not commit that cache. Build/test logs
belong outside the repository. The root project POM and our RBAC backends remain
unchanged. Upstream Boot **4.1.2**, Lombok **1.18.46**, its 449 original source files
and CloneFactory compiled successfully on JDK21; no compatibility downgrade/upgrade
was needed. This does **not** upgrade our Boot 3 parent. The scripts use explicit,
credential-free Maven settings rather than reading user settings.

## Actual upstream gaps repaired

* Upstream has Security*Authorization consumers, but no resource-server JWT chain or
  authority loader. The integration profile verifies **RS256 only**, issuer, expiry,
  and `aud=course-project-management` locally using configured JWKS.
* An MVC interceptor maps the server's resolved route and HTTP verb to the pinned
  OpenAPI operationId. Unknown routes fail closed. Incoming operationId headers are
  irrelevant. Every business request first calls `/auth/api-permissions/{operationId}`;
  false never reaches the business controller. No decisions are cached.
* Only after permission succeeds does an `@HttpExchange` client call internal
  `GET /integration/authorities` with the verified user bearer and backend service key.
  Those current API authority codes and `ROLE_teacher/student/admin` replace the
  SecurityContext authorities. JWT role/permission claims are ignored. Local Teacher
  and Student IDs are resolved by the verified `sub`, not a claimed name or numeric ID.
* Copy-course checks actual teacher qualification before any source/target lookup or
  leader/CC authorization. An actual source leader can copy without CC; a nonleader
  needs actual `CC`; allowed(operation)=true is never substituted for CC. A local
  teacher record without a current central ROLE_teacher is not a teacher session.
* Both existing Audit and Identity HTTP proxies use finite connect/read timeouts and
  overwrite `X-Audit-Service-Key` with the configured backend secret. No incoming
  service-key header is forwarded. No token/key/upstream error bodies are logged.
* The upstream audit client had **no write callers**. The integration now synchronously
  writes the teacher contract's `score_changed`, `group_changed`, `milestone_changed`,
  `roster_changed` families after successful domain operations. Oral changes include
  real pre-change DB scores and actual returned post-change scores. Audit snapshots
  are minimized to the center's safe-field schema; oral memos are not leaked.
* Central audit responses do not contain upstream's top-level course/term/class scope
  IDs. The gateway extracts the trusted context IDs it previously wrote into `after`,
  allowing actual existing teacher visibility filters to work for GET `/audit-logs`.
* Two old test fixtures needed explicit identities: missing-source copy now supplies
  a real Teacher before expecting 404; the class-teacher denial test now uses a token
  without CTM rather than the default test token that grants every authority. Assertions
  and all original tests are retained. No business service is replaced with a fake.

## Audit guarantees and the unavoidable window

`X-Request-ID` is accepted only as a UUID (otherwise generated), echoed in the response,
and used unchanged for the external audit request. A transport timeout gets at most
one finite, synchronous retry of the **same immutable body and requestId**. Center
requestId idempotency is the authoritative duplicate guard. HTTP 4xx/5xx are not
retried. Missing/non-successful acknowledgements produce HTTP503
`AUDIT_UNCONFIRMED`, not an audited-success response.

Course and center databases are **not one transaction**. The domain transaction can
commit before audit acknowledgement, or the center can commit before its response is
lost. A crash in that interval can leave committed business data and absent/unconfirmed
audit. There is intentionally no MQ, outbox, background retry, distributed transaction,
or claim that business replay is idempotent. Do not blindly replay the entire business
request after 503; reconcile by requestId and inspect the business state. The HTTP test
explicitly demonstrates the committed-business / audit-unconfirmed case instead of
pretending a test can eliminate this window. `X-Audit-State` distinguishes confirmed,
unconfirmed and non-audited operation families. Course/term administrative mutations
are not falsely labelled as one of the four unrelated teacher audit event families.

## HTTP verification and dedicated fixture

`tests/fixture.sql` contains **only synthetic m6-* users** and fixed IDs 610001/610002.
Do not apply it to a shared/old/production database. The boundary runner hardcodes its
own `m6-course-mysql` container and `course_integration_boundary` database, checks real
Teacher/Student subject mappings, runs the real executable/JPA/services, and simulates
**only** the central HTTP boundary with an ephemeral RS256 keypair. No CourseService,
controller, repository, or core domain mock is used.

```bash
export COURSE_LOG='C:/Users/vintces/Downloads/m6-course-boundary-server.log'
# Default jar is .cache/course-source/prepared/backend/target/...jar
node course-integration/tests/http-boundary.mjs
COURSE_STRATEGY=ddd node course-integration/tests/http-boundary.mjs
```

This covers: real term creation, teacher course submission, administrator review,
leader/no-CC and nonleader/CC copy branches, student-leader join approval, and oral
score mutation/audit readback. It additionally exercises wrong aud/issuer/signature,
expired JWT, denied operation with no authority call, forged JWT authorities,
untrusted service-key header, timeout-after-center-commit requestId deduplication, and
post-domain-commit audit failure. This is an **HTTP boundary test**, not proof that the
separately deployed real center has passed end-to-end acceptance.

`tests/live-six-api.mjs` performs the same six real API classes through the actual
same-origin gateway (default8090), using five real central user tokens from env:
`COURSE_ADMIN_TOKEN`, `COURSE_LEADER_TOKEN`, `COURSE_CC_TEACHER_TOKEN`,
`COURSE_NO_CC_TEACHER_TOKEN`, `COURSE_STUDENT_LEADER_TOKEN`. It refuses execution
without `COURSE_DEDICATED_DB_CONFIRM=course_integration`. Provision corresponding
synthetic subjects/central authorities in the dedicated integration deployment first.
The live runner never obtains or transmits the internal audit service key.

The preserved upstream suite requires MySQL. Its default `mysql` hostname is not a
local Windows server. The dedicated loopback runner uses its own MySQL8 container,
small pools and a test-only `sslMode=DISABLED` URL to avoid intermittent Docker-local
TLS handshake termination. Production TLS policy is not downgraded by this setting.

## Deploy / frontend

`build-images.sh` emits `ooad/course-backend:m6` and `ooad/course-frontend:m6` (override
`COURSE_IMAGE_PREFIX`). Backend Docker context includes only the built teacher jar
and MIT license via `.dockerignore`. No roster or build tool enters the runtime image.
Frontend is a small real login/role-menu/API client, not the upstream static-data
prototype. It signs into central `/auth/login` requesting course audience; lists roles
from central `/auth/me`; calls real `/api/me`, terms, role-specific course lists,
and supports real JSON course requests. Menus do not grant server authority. Only the
user bearer is stored in sessionStorage, never a service key. There is no fake dataset.

The deployment gateway must route `/auth/*` and JWKS to center8081, `/api/*`
(stripping `/api`) to course-backend8080, and `/course/*` page/assets to course-frontend80 (base path `/course/`).
`swarm-course.yaml` provides private-network service entries with no published course
backend port. Attach to gateway network via COURSE_NETWORK and set actual issuer/JWK.
Secrets `audit-service-key` and `course-db-password` mount via configtree. BaseURL,
issuer, JWK and key are configuration, not source credentials. The sample does not
create or repurpose an existing database, network, gateway, or center.
