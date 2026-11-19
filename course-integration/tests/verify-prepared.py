"""Read-only verification of the exported/overlaid teacher tree; no database access."""
import json
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else '.cache/course-source/prepared')
lock = pathlib.Path(__file__).resolve().parents[1] / 'UPSTREAM.lock'
commit = next(line.removeprefix('commit=') for line in lock.read_text().splitlines() if line.startswith('commit='))
assert (root / 'UPSTREAM_COMMIT').read_text().strip() == commit
assert (root / 'deploy/stack.yml').is_file(), 'Teacher logging convention test requires the pinned stack configuration'
assert 'MIT License' in (root / 'LICENSE').read_text(encoding='utf-8')
assert not list(root.rglob('*.xlsx')), 'Personnel spreadsheets must never be exported'
assert not list(root.rglob('.env*')), 'Local environment files must never be exported'
api = json.loads((root / 'api/openapi.json').read_text(encoding='utf-8'))
expected = {}
for path, methods in api['paths'].items():
    if path.startswith('/auth/'):
        continue
    for method, operation in methods.items():
        if isinstance(operation, dict) and 'operationId' in operation:
            key = method.upper() + ' ' + re.sub(r'\{[^}]+}', '{}', path)
            expected[key] = operation['operationId']
routes = dict(line.strip().split('\t') for line in (root / 'backend/src/main/resources/integration-routes.tsv').read_text().splitlines() if line.strip())
assert routes == expected, 'Trusted operation map must exactly match the pinned contract'
assert all(re.fullmatch(r'[A-Za-z][A-Za-z0-9]*', op) for op in routes.values())
assert 'spring-boot-starter-security-oauth2-resource-server' in (root / 'backend/pom.xml').read_text()
assert (root / 'backend/src/main/java/cn/edu/xmu/projectmgt/integration/CentralSecurity.java').exists()
print(f'PASS fixed commit/MIT/privacy/overlay/{len(routes)} exact trusted routes')
