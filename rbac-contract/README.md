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

The last command **drops and recreates only `rbac1_schema_contract_test`**, after verifying container `rbac-phase2-test-mysql` is bound to loopback port 13306. It uses real cached Flyway 10.10.0 and MySQL JDBC jars, migrates V1/V2, validates checksums, repeats migration (zero executions), checks all 15 physical column lists against the frozen document, checks catalogs/grants/mappings and negative database constraints. MySQL queries run through `docker exec`; credentials are neither printed nor passed as CLI arguments. It never connects to or modifies `rbac_test`, production or application test schemas. Static generation/check has no database access.

`target/` may contain stale resources from the previous build: run a **clean** Maven build before integrating the jar. Flyway has exactly `V1__rbac1_schema.sql` and `V2__rbac1_seed.sql`; old V1–V4 remain only in Git history. No baseline-on-migrate for existing schemas.

## Source and catalogs

* `catalog/course-openapi.source.yaml`: byte-for-byte teacher OAS snapshot, repository commit `fde08d5dd417abc543145aff33ca8378deb9ca36`.
* SHA-256: `4c944b94b0b18cbd20aa0091c20954927129bc586e97bb73dd79049f2814e40c` (pinned and checked by generator).
* `catalog/course-operations.json`: all 80 real business operations, original method/path/operationId/metadata, effective registration policy and the seven explicitly excluded identity provider operations.
* `catalog/ui-api-coverage.csv`: explicit seeded UI↔API links with operationId, method, path, permissionCode, requiredRole, category and resource/operation IDs. Multiple rows mean multiple UI mappings, not multiple registrations or implicit grants.
* `catalog/coverage-samples.json`: ten real samples across terms/administration, courses, roster, groups, submissions, milestones, oral, grades and files.

Center catalog registers all 62 OAS operations: 52 internal operations, seven external identity operations and three public JWKS/actuator operations. All 52 internal paths explicitly start `/api/v1`; the OAS server is a root URL. Every operation has `x-required-permission` (`PUBLIC`, `@authenticated`, or an API permission code). Course catalog excludes the seven provider endpoints. No missing metadata becomes PUBLIC: it receives `course:access`; explicit required roles are also retained. `copyCourse` uses `required_role=teacher`, never CC.

## Seed and enforcement boundaries

Two systems, HQ/Finance/Engineering, center SUPER_ADMIN/RBAC_ADMIN/AUDITOR and course teacher/student/admin are explicit. All role admissions are enumerated; SUPER_ADMIN admission exists only at HQ. The only user and assignment are root (`id=1`, subject `rbac-u1`) and its center SUPER_ADMIN role. Other user IDs start at 10000. Root hash is **UNINITIALIZED**, never a public password; application bootstrap must hash `RBAC_BOOTSTRAP_PASSWORD` only while that sentinel exists.

Center has 12 MENU pages plus distinct UI BUTTON permissions. SUPER_ADMIN has explicit API/UI grants; RBAC_ADMIN omits system-edit API/UI; AUDITOR receives read API/menu grants, no management mutation buttons. Course admin explicitly receives every actual API permission plus course audit permissions. Teacher API grants are exactly course:access, audit:read, audit:write, CRS; student API grants are exactly course:access, audit:write. Course UI permissions are separate per role, with curated teacher/student operation mappings. No course role is assigned to root or other users automatically. UI mapping never grants API authority or bypasses requiredRole/object rules.

Foreign keys, unique keys and representable CHECKs guard database invariants. Same-system joins, UI/API permission kind, graph/tree acyclicity, requiredRole authorization, department admission and last-super-admin protection remain transaction-layer obligations under the global grant-version lock. MySQL rejects CHECK expressions referencing AUTO_INCREMENT id, so tree self/cycle guards are deliberately transactional rather than invalid DDL. Audit records have no business foreign keys.
