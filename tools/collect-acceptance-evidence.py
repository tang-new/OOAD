#!/usr/bin/env python3
"""Derive a portable, secret-free summary from actual reports; never execute/fabricate tests."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ensure_portable(value):
    """Refuse credential-shaped output instead of silently masking test evidence."""
    if isinstance(value, dict):
        for key, item in value.items():
            if re.search(r'password|authorization|accessToken|refreshToken|privateKey|serviceKey', key, re.I):
                raise ValueError('Credential-shaped field cannot enter portable evidence')
            ensure_portable(item)
    elif isinstance(value, list):
        for item in value:
            ensure_portable(item)
    elif isinstance(value, str):
        if 'PRIVATE KEY-----' in value or re.search(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', value):
            raise ValueError('Key/JWT-shaped value cannot enter portable evidence')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--shared-report', type=Path, required=True)
    parser.add_argument('--verify-log', type=Path, required=True)
    parser.add_argument('--course-report', type=Path, required=True)
    parser.add_argument('--frontend-unit', type=Path, required=True)
    parser.add_argument('--browser-log', type=Path, action='append', required=True)
    args = parser.parse_args()
    out = ROOT / 'docs/acceptance'
    shared = json.loads(args.shared_report.read_text(encoding='utf8'))
    assert shared['exitCode'] == 0 and shared['summary'] == {'pass': 51, 'fail': 0}
    assert all(row['status'] == 'pass' for row in shared['rows'])
    verify = args.verify_log.read_text(encoding='utf8')
    assert 'BUILD SUCCESS' in verify and 'BUILD FAILURE' not in verify
    assert verify.count('All coverage checks have been met.') == 3
    result = {'format': 'rbac1-portable-local-evidence-v1', 'scope': 'Derived from actual normal JUnit, real HTTP, browser and course reports. Raw logs remain outside Git; no external deployment or CI claim.', 'junit': {}, 'coverage': {}, 'sharedHttp': {'pass': 51, 'fail': 0, 'sourceReportSha256': sha(args.shared_report)}, 'verify': {'log': args.verify_log.name, 'sha256': sha(args.verify_log), 'status': 'BUILD SUCCESS', 'phase': 'verify after prior normal JUnit and HTTP; no repeated JUnit in this phase'}}
    for module, minimum in [('rbac-contract', 2), ('rbac-structured', 49), ('rbac-ddd', 112), ('rbac-gateway', 25)]:
        counts = {key: 0 for key in ['tests', 'failures', 'errors', 'skipped']}
        for file in (ROOT/module/'target/surefire-reports').glob('TEST-*.xml'):
            suite = ET.parse(file).getroot()
            for key in counts:
                counts[key] += int(suite.get(key, '0'))
        assert counts['tests'] >= minimum and not any(counts[k] for k in ['failures', 'errors', 'skipped'])
        result['junit'][module] = counts
        if module != 'rbac-contract':
            rows = list(csv.DictReader((ROOT/module/'target/site/jacoco/jacoco.csv').open()))
            groups = {'all': rows}
            business = [row for row in rows if row['PACKAGE'].endswith(('.service', '.application'))]
            if business:
                groups['serviceOrApplication'] = business
            result['coverage'][module] = {}
            for name, group in groups.items():
                result['coverage'][module][name] = {}
                for counter in ['LINE', 'BRANCH']:
                    covered = sum(int(row[counter+'_COVERED']) for row in group)
                    missed = sum(int(row[counter+'_MISSED']) for row in group)
                    result['coverage'][module][name][counter] = {'covered': covered, 'missed': missed, 'percent': round(100*covered/(covered+missed), 4)}
    portable = {k: shared[k] for k in ['format', 'startedAt', 'finishedAt', 'command', 'rows', 'exitCode', 'summary']}
    portable['modules'] = {}
    for module, entry in shared['modules'].items():
        artifact = ROOT/f'rbac-{module}/target/rbac-{module}.jar'
        assert sha(artifact) == entry['jarSha256'], 'Actual tested snapshot differs from canonical'
        portable['modules'][module] = {'jarSha256': entry['jarSha256'], 'logDirectory': 'retained outside repository', 'database': 'unique disposable fixture, cleaned', 'ports': entry['ports']}
    course = json.loads(args.course_report.read_text(encoding='utf8'))
    assert course['passed'] and len(course['modules']) == 2
    result['course'] = {'sourceReportSha256': sha(args.course_report), 'upstream': course['upstream'], 'modules': {}}
    for module, entry in course['modules'].items():
        assert entry['passed'] and len(entry['rows']) == 6 and all(row['passed'] for row in entry['rows'])
        assert all(entry['cleanup'].values()) and entry['persisted'] == {'score': 88, 'approvedMemberships': 1}
        audit = next(row for row in entry['rows'] if row['name'] == 'oral-score-audit-POST-GET')['result']
        assert audit['before'] == 70 and audit['after'] == 88
        jars = {name: data['candidateSHA256'] for name, data in entry['jars'].items()}
        assert jars['center'] == portable['modules'][module]['jarSha256']
        result['course']['modules'][module] = {'pass': 6, 'cleanup': entry['cleanup'], 'jars': jars, 'persisted': entry['persisted'], 'audit': audit}
    unit = json.loads(args.frontend_unit.read_text(encoding='utf8'))
    assert unit['success'] and unit['numFailedTests'] == 0 and unit['numTotalTests'] == 38
    result['frontendUnit'] = {'pass': 38, 'sourceReportSha256': sha(args.frontend_unit)}
    assert len(args.browser_log) == 2
    result['browser'] = []
    for log in args.browser_log:
        text = re.sub(r'\x1b\[[0-9;]*m', '', log.read_text(encoding='utf8'))
        assert re.search(r'\b2 passed\b', text) and not re.search(r'\b[1-9][0-9]* failed\b', text)
        result['browser'].append({'log': log.name, 'sha256': sha(log), 'pass': 2, 'scope': 'one backend, desktop and mobile, loaded-state screenshots'})
    result['junitTotal'] = sum(entry['tests'] for entry in result['junit'].values())
    ensure_portable(portable)
    ensure_portable(result)
    out.mkdir(exist_ok=True)
    (out/'shared-http.json').write_text(json.dumps(portable, ensure_ascii=False, indent=2)+'\n', encoding='utf8', newline='\n')
    (out/'local-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf8', newline='\n')
    print('Portable verified evidence written: docs/acceptance/local-results.json and shared-http.json')


if __name__ == '__main__':
    main()
