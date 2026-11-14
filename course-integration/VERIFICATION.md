> Final follow-up: the current default canonical structured + DDD targets and current `rbac-gateway.jar` passed all 12 real-course scenarios. See `REAL-CENTERS-VERIFICATION.md` and external `m18-course-real-current.log`. This is not the old final47 run; older image IDs below are historical.

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
