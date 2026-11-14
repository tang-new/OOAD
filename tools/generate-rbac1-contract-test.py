#!/usr/bin/env python3
"""Static validation + REAL Flyway migration on the dedicated MySQL container only.
Requires Java 21, cached Maven Flyway 10.10.0/MySQL/Jackson/Gson jars,
PyYAML and openapi-spec-validator. Recreates ONLY rbac1_schema_contract_test.
Never touches rbac_test or application/production schemas.
"""
import json, os, re, subprocess, sys, tempfile
from pathlib import Path
import yaml
from openapi_spec_validator import validate_spec
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'rbac-contract/src/main/resources'
CONTAINER='rbac-phase2-test-mysql'
DATABASE='rbac1_schema_contract_test'
def run(args, **kwargs):return subprocess.run(args,check=True,**kwargs)
def sql(query, expect_failure=False):
    # Root secret stays inside the dedicated container, not arguments or output.
    p=subprocess.run(['docker','exec','-i',CONTAINER,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N '+DATABASE],input=query,text=True,encoding='utf8',capture_output=True)
    if expect_failure:
        assert p.returncode!=0, 'Constraint unexpectedly accepted invalid data: '+query
    elif p.returncode:
        raise RuntimeError(p.stderr)
    return p.stdout.strip()
def main():
    run([sys.executable,str(ROOT/'tools/generate-rbac1-contract.py'),'--check'])
    doc=yaml.safe_load((RES/'openapi/rbac-api.yaml').read_text(encoding='utf8'));validate_spec(doc)
    print('OpenAPI semantic/$ref/operationId validation PASS')
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    version=subprocess.check_output([java,'-version'],stderr=subprocess.STDOUT,text=True,encoding='utf8',errors='replace')
    major=re.search(r'(?:version\s+\"|openjdk\s+)(\d+)',version)
    if not major or int(major.group(1))<21:
        raise RuntimeError('Java 21+ required; set JAVA_HOME before recreating the dedicated test schema')
    info=json.loads(subprocess.check_output(['docker','inspect',CONTAINER],text=True))[0]
    bindings=info['HostConfig']['PortBindings']['3306/tcp']
    assert any(b['HostPort']=='13306' and b['HostIp'] in ['127.0.0.1','::1'] for b in bindings), 'Unexpected MySQL instance; abort'
    # Only this exact disposable schema is ever dropped.
    run(['docker','exec',CONTAINER,'sh','-c',f'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE IF EXISTS {DATABASE}; CREATE DATABASE {DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"'])
    env=os.environ.copy()
    env['RBAC_CONTRACT_TEST_PASSWORD']=next(x.split('=',1)[1] for x in info['Config']['Env'] if x.startswith('MYSQL_ROOT_PASSWORD='))
    repo=Path.home()/'.m2/repository'
    groups=['org/flywaydb/flyway-core/10.10.0','org/flywaydb/flyway-mysql/10.10.0','com/mysql/mysql-connector-j','com/fasterxml/jackson/core/jackson-core','com/fasterxml/jackson/core/jackson-databind','com/fasterxml/jackson/core/jackson-annotations','com/fasterxml/jackson/dataformat/jackson-dataformat-toml','com/google/code/gson/gson']
    jars=[]
    for group in groups:
        candidates=[p for p in (repo/group).glob('**/*.jar') if not any(s in p.name for s in ['-sources','-javadoc'])]
        assert candidates, 'Missing cached dependency '+group
        # Prefer one recent cached version, never mix all versions of a dependency.
        jars.append(sorted(candidates,key=lambda p:tuple(int(v) if v.isdigit() else v for v in p.parent.name.split('.')))[-1])
    source='''import org.flywaydb.core.Flyway;
class ContractMigrationTest {
 public static void main(String[] args) {
  Flyway f=Flyway.configure().dataSource("jdbc:mysql://127.0.0.1:13306/rbac1_schema_contract_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", "root", System.getenv("RBAC_CONTRACT_TEST_PASSWORD")).locations("filesystem:"+args[0]).cleanDisabled(true).load();
  var first=f.migrate(); if(first.migrationsExecuted!=2) throw new AssertionError("Expected exactly V1/V2");
  f.validate();var second=f.migrate();if(second.migrationsExecuted!=0)throw new AssertionError("Repeat migration not idempotent");
  System.out.println("Flyway migrate V1/V2, validate, repeat migrate PASS");
 }
}'''
    with tempfile.TemporaryDirectory(prefix='rbac1-contract-') as temp:
        src=Path(temp)/'ContractMigrationTest.java';src.write_text(source,encoding='utf8')
        run([java,'-cp',os.pathsep.join(map(str,jars)),str(src),str(RES/'db/migration')],env=env)
    assert sql('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE \'sys_%\';')=='15'
    assert sql("SELECT password_hash FROM sys_user WHERE id=1;")=='UNINITIALIZED'
    assert sql('SELECT COUNT(*) FROM sys_user_role;')=='1'
    assert sql('SELECT COUNT(*) FROM sys_api_operation WHERE system_id=2;')=='80'
    assert sql("SELECT required_role FROM sys_api_operation WHERE system_id=2 AND operation_id='copyCourse';")=='teacher'
    assert sql("SELECT COUNT(*) FROM sys_api_operation WHERE system_id=2 AND access_policy<>'PROTECTED';")=='0'
    assert sql("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.system_id=2 AND r.code='teacher' AND p.kind='API' AND p.code NOT IN ('course:access','audit:read','audit:write','CRS');")=='0'
    assert sql("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.system_id=2 AND r.code='student' AND p.kind='API' AND p.code NOT IN ('course:access','audit:write');")=='0'
    assert sql("SELECT COUNT(*) FROM sys_api_operation a WHERE a.system_id=2 AND a.permission_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE r.system_id=2 AND r.code='admin' AND rp.permission_id=a.permission_id);")=='0'
    assert sql("SELECT COUNT(*) FROM sys_ui_resource WHERE system_id=1 AND type='MENU';")=='12'
    assert sql("SELECT COUNT(*) FROM sys_api_operation a WHERE access_policy='PROTECTED' AND NOT EXISTS(SELECT 1 FROM sys_ui_api_mapping m WHERE m.api_operation_id=a.id);")=='0'
    # Exact physical columns must match the frozen schema, including nullable projections.
    contract=(ROOT/'docs/schema-contract.adoc').read_text(encoding='utf8')
    for table,definition in re.findall(r'^\|(sys_\w+) \|(.+)$',contract,re.M):
        expected=[part.strip().split()[0] for part in definition.split('；')[0].split(',') if part.strip()]
        actual=sql(f'SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=\'{table}\' ORDER BY ordinal_position;').splitlines()
        # Inline SQL declarations UNIQUE(system_id,code) contain commas; take columns before UNIQUE.
        expected=[x for x in expected if not x.startswith('UNIQUE')]
        assert actual==expected,(table,actual,expected)
    invalid=[
      "INSERT INTO sys_system(code,name,status) VALUES('invalid','invalid','BAD');",
      "INSERT INTO sys_role(system_id,code,name,status,identity_role) VALUES(2,'invalid','invalid','ENABLED','SUPER_ADMIN');",
      "INSERT INTO sys_permission(system_id,code,name,kind,status) VALUES(1,'invalid','invalid','INVALID','ENABLED');",
      "INSERT INTO sys_user_role(user_id,role_id) VALUES(999999999,1);",
      "INSERT INTO sys_user_role(user_id,role_id) VALUES(1,1);",
      "INSERT INTO sys_role_hierarchy(senior_role_id,junior_role_id) VALUES(1,1);",
      "INSERT INTO sys_grant_version(id,version) VALUES(2,0);",
      "INSERT INTO sys_api_operation(system_id,operation_id,method,path_template,access_policy) VALUES(2,'invalid','GET','/invalid','PROTECTED');",
      "INSERT INTO sys_ui_api_mapping(ui_resource_id,api_operation_id) SELECT ui_resource_id,api_operation_id FROM sys_ui_api_mapping LIMIT 1;"
    ]
    for query in invalid:sql(query,expect_failure=True)
    sql('START TRANSACTION; INSERT INTO sys_user(subject,username,password_hash,name,department_id,status) VALUES(\'test-reservation\',\'test-reservation\',\'UNINITIALIZED\',\'Test\',1,\'ACTIVE\'); SELECT LAST_INSERT_ID(); ROLLBACK;')
    assert int(sql("SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_user';"))>=10000
    assert sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;')=='2'
    print(f'MySQL {DATABASE}: 15 exact-column tables, all catalogs/grants/mappings, root placeholder, ID reservation and {len(invalid)} negative constraint tests PASS')
if __name__=='__main__':main()
