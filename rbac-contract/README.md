# RBAC1 shared contract

Frozen specifications: `docs/implementation-contract.adoc` and `docs/schema-contract.adoc` (repository root). This module shares only protocol, catalogs and empty-development-database migrations, never authorization algorithms.

## Reproduce and validate

```sh
python tools/generate-rbac1-contract.py
python tools/generate-rbac1-contract.py --check
# Install locally if needed: python -m pip install PyYAML openapi-spec-validator
# JAVA_HOME must point to Java 21, not an older java.exe early in PATH.
python tools/generate-rbac1-contract-test.py
```

The last command **drops and recreates only `rbac1_schema_contract_test`** after verifying that `rbac-phase2-test-mysql` is bound to loopback port 13306. It applies V1/V2, adds a custom edit-only role, upgrades through V3 and V4, validates checksums and checks that a second migration runs zero scripts. It checks the effective catalogs, grants, mappings, table columns and database constraints. MySQL queries run through `docker exec` with credentials kept in the container. Static generation/check uses local files.

`target/` may contain stale resources from the previous build. Run a **clean** Maven build before integrating the jar. Flyway applies V1/V2 for the RBAC1 baseline, V3 for the dedicated role-status capability, and V4 for identity directory operations and public course login. The RBAC0 migration history remains in Git.

## Source and catalogs

* `catalog/course-openapi.source.yaml`: byte-for-byte teacher OAS snapshot, repository commit `7f27e605f37f540253c3f2ff73227424250a4ec8`.
* SHA-256: `8a309b5a475b9e24b2146f4ae112b8d0a9f291f48e4556b121758ada440b2d84` (pinned and checked by generator).
* `catalog/course-operations.json`: 80 course operations including public POST /login, with method, path, operationId, registration policy and ten excluded provider operations.
* `catalog/ui-api-coverage.csv`: explicit seeded UI↔API links with operationId, method, path, permissionCode, requiredRole, category and resource/operation IDs. Multiple rows mean multiple UI mappings, not multiple registrations or implicit grants.
* `catalog/coverage-samples.json`: ten real samples across terms/administration, courses, roster, groups, submissions, milestones, oral, grades and files.

The center catalog contains 67 operations, including eight external identity operations and two audit operations. The three directory queries require the course audience, admin role, and course API capability TEC. The course catalog contains 79 protected operations and the public POST /login. copyCourse retains its teacher requirement and applies the leader/CC rule in the course service.

## Seed and enforcement boundaries

Two systems, HQ/Finance/Engineering, center SUPER_ADMIN/RBAC_ADMIN/AUDITOR and course teacher/student/admin are explicit. All role admissions are enumerated; SUPER_ADMIN admission exists only at HQ. The only user and assignment are root (`id=1`, subject `rbac-u1`) and its center SUPER_ADMIN role. Other user IDs start at 10000. Root hash is **UNINITIALIZED**, never a public password; application bootstrap must hash `RBAC_BOOTSTRAP_PASSWORD` only while that sentinel exists.

Center has 12 MENU pages plus distinct UI BUTTON permissions. SUPER_ADMIN has explicit API/UI grants; RBAC_ADMIN omits system-edit API/UI; AUDITOR receives read API/menu grants, no management mutation buttons. Course admin explicitly receives every actual API permission plus course audit permissions. Teacher API grants are exactly course:access, audit:read, audit:write, CRS; student API grants are exactly course:access, audit:write. Course UI permissions are separate per role, with curated teacher/student operation mappings. No course role is assigned to root or other users automatically. UI mapping never grants API authority or bypasses requiredRole/object rules.

Foreign keys, unique keys and representable CHECKs guard database invariants. Same-system joins, UI/API permission kind, graph/tree acyclicity, requiredRole authorization, department admission and last-super-admin protection remain transaction-layer obligations under the global grant-version lock. MySQL rejects CHECK expressions referencing AUTO_INCREMENT id, so tree self/cycle guards are deliberately transactional rather than invalid DDL. Audit records have no business foreign keys.
