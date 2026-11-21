import assert from 'node:assert/strict'
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'

function fixture(t) {
  const dir = mkdtempSync(join(tmpdir(), 'course-fetch-test-'))
  t.after(() => rmSync(dir, { recursive: true, force: true }))
  for (const path of ['tools', 'bin', 'course-integration/overlay', 'course-integration/patches', 'archive/backend', 'archive/api', 'archive/prototype', 'archive/deploy']) mkdirSync(join(dir, path), { recursive: true })
  copyFileSync(new URL('./prepare-course.sh', import.meta.url), join(dir, 'tools/prepare-course.sh'))
  copyFileSync(new URL('../course-integration/UPSTREAM.lock', import.meta.url), join(dir, 'course-integration/UPSTREAM.lock'))
  writeFileSync(join(dir, 'archive/deploy/stack.yml'), 'services: {}\n')
  writeFileSync(join(dir, 'archive/LICENSE'), 'MIT synthetic test fixture\n')
  writeFileSync(join(dir, 'bin/sleep'), '#!/usr/bin/env bash\nexit 0\n', { mode: 0o755 })
  writeFileSync(join(dir, 'bin/git'), `#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$CALL_LOG"
case "$*" in
  *'init -q'*) mkdir -p "$3/.git";;
  *'fetch --depth=1'*)
    n=0; [[ ! -f "$COUNTER" ]] || n=$(<"$COUNTER")
    n=$((n+1)); echo "$n" > "$COUNTER"
    [[ "$n" -gt "$FAIL_COUNT" ]] || exit 128;;
  *'cat-file -e'*) [[ "$VERIFY_FAIL" != 1 ]] || exit 1;;
  *'archive '*) exec tar -cf - -C "$ARCHIVE_ROOT" backend api prototype deploy/stack.yml LICENSE;;
esac
`, { mode: 0o755 })
  return (extra = {}) => {
    const posix = name => join(dir, name).replaceAll('\\', '/')
    const result = spawnSync(process.env.BASH_TEST_SHELL ?? 'bash', [
      '-c', 'export PATH="$PWD/bin:$PATH"; exec bash "$PWD/tools/prepare-course.sh"',
    ], {
      cwd: dir, encoding: 'utf8', env: {
        ...process.env, COURSE_SOURCE: '', COURSE_CACHE: posix('cache'), CALL_LOG: posix('calls'),
        COUNTER: posix('count'), ARCHIVE_ROOT: posix('archive'), FAIL_COUNT: '0', VERIFY_FAIL: '0', ...extra,
      },
    })
    const calls = join(dir, 'calls')
    return { ...result, calls: existsSync(calls) ? readFileSync(calls, 'utf8') : '' }
  }
}

test('CI exports before the isolated course database verifier and builds the real frontend separately', () => {
  const ci = readFileSync(new URL('../.github/workflows/ci.yml', import.meta.url), 'utf8')
  assert.doesNotMatch(ci, /bash tools\/prepare-course\.sh --build/)
  assert.ok(ci.indexOf('bash tools/prepare-course.sh') < ci.indexOf('bash course-integration/tests/run-domain-tests.sh'))
  assert.ok(ci.includes('working-directory: course-integration/frontend'))
})

test('course transport retries remain bounded and never export after three failures', t => {
  const result = fixture(t)({ FAIL_COUNT: '9' })
  assert.equal(result.status, 128, result.stderr)
  assert.equal((result.calls.match(/fetch --depth=1/g) ?? []).length, 3)
  assert.doesNotMatch(result.calls, /cat-file|archive /)
})

test('course transport may recover but must verify the exact pinned commit before export', t => {
  const result = fixture(t)({ FAIL_COUNT: '2' })
  assert.equal(result.status, 0, result.stderr)
  assert.equal((result.calls.match(/fetch --depth=1/g) ?? []).length, 3)
  assert.match(result.calls, /cat-file -e e9a6dff454cc6af23297458320f9c58d469850c9\^\{commit\}/)
  assert.ok(result.calls.indexOf('cat-file') < result.calls.indexOf('archive '))
  assert.doesNotMatch(result.calls, /sslVerify=false|sslverify=false/)
})

test('successful transport with unavailable pinned commit never exports', t => {
  const result = fixture(t)({ VERIFY_FAIL: '1' })
  assert.notEqual(result.status, 0)
  assert.match(result.calls, /cat-file/)
  assert.doesNotMatch(result.calls, /archive /)
})
