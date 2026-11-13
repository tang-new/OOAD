import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const root = new URL('../', import.meta.url)
const text = (path) => readFileSync(new URL(path, root), 'utf8')

// 阶段一门槛：历史资料可留在Git中，但废弃模块不能再成为运行依赖。
test('M1: 聚合与部署入口不再构建一期外部演示模块', () => {
  for (const path of ['pom.xml', 'docker-compose.yml', 'Dockerfile', 'deploy/build.sh']) {
    assert.doesNotMatch(text(path), /rbac-mock-biz/, path)
  }
  assert.equal(existsSync(new URL('rbac-mock-biz/pom.xml', root)), false)
})

test('M1: 冻结人工审查包退出当前工程', () => {
  assert.equal(existsSync(new URL('human-review/', root)), false)
})

test('M1: 当前文档入口和需求源存在，不继续依赖一期文档导航', () => {
  for (const path of [
    'docs/requirements.adoc', 'docs/migration-baseline.adoc',
    'docs/architecture-structured.adoc', 'docs/architecture-ddd.adoc',
    'docs/database.adoc', 'docs/api.adoc', 'docs/test-plan.adoc',
    'docs/deployment.adoc', 'docs/iteration-log.adoc',
    'docs/sources/requirements-final.md',
  ]) {
    assert.ok(existsSync(new URL(path, root)), fileURLToPath(new URL(path, root)))
  }
  assert.doesNotMatch(text('README.md'), /docs\/(?:0\d|1[0-7])-|human-review\//)
})

test('M1: 当前 adoc 的本地文件链接均可解析', () => {
  const docs = new URL('docs/', root)
  for (const file of readdirSync(docs).filter((name) => name.endsWith('.adoc'))) {
    const content = readFileSync(new URL(file, docs), 'utf8')
    for (const [, target] of content.matchAll(/link:([^\s\[]+)\[/g)) {
      if (/^https?:/.test(target)) continue
      const path = target.split('#')[0]
      assert.ok(existsSync(new URL(path, docs)), `${file} → ${target}`)
    }
  }
})
