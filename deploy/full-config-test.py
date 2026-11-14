"""Deployment safety regression tests; no daemon writes, real credentials, or mock business."""
import os, pathlib, shutil, subprocess, tempfile, unittest
import yaml
ROOT = pathlib.Path(__file__).resolve().parents[1]
class Deployment(unittest.TestCase):
 def setUp(self):
  self.doc=yaml.safe_load((ROOT/'docker-compose.yml').read_text(encoding='utf8'))
 def test_seven_real_services_and_private_ports(self):
  services=self.doc['services']
  self.assertEqual(set(services),{'mysql','redis','rbac-backend','rbac-gateway','rbac-web','course-backend','course-web'})
  for name,service in services.items():
   if name not in ('rbac-web','course-web'): self.assertNotIn('ports',service)
   self.assertEqual(service['deploy'].get('replicas',1),1)
  self.assertNotIn('mysql-root-password',str(services['rbac-backend']))
  self.assertNotIn('jwt-private',str(services['course-backend']))
  self.assertNotIn('audit-service-key',str(services['rbac-gateway']))
 def test_secrets_are_file_backed_not_literals(self):
  for config in self.doc['secrets'].values(): self.assertIn('file',config)
  center=str(self.doc['services']['rbac-backend'])
  self.assertIn('configtree:/run/secrets/',center)
  self.assertIn('spring.datasource.password',center)
 def test_nginx_denies_private_interfaces_and_fixed_upstreams(self):
  for file in ['rbac-web/nginx.conf','deploy/full-course-nginx.conf']:
   value=(ROOT/file).read_text(encoding='utf8')
   self.assertIn('(integration|identity)',value);self.assertIn('return 403',value)
   self.assertIn('listen 443 ssl',value);self.assertNotIn('$http_host',value)
   self.assertIn('proxy_set_header X-Audit-Service-Key ""',value)
 def test_explicit_environment_precedes_dotenv_and_rejects_module(self):
  with tempfile.TemporaryDirectory() as tmp:
   root=pathlib.Path(tmp);(root/'deploy').mkdir()
   for name in ['full-env.sh','backend-module.sh']: shutil.copy(ROOT/'deploy'/name,root/'deploy'/name)
   (root/'.env').write_text('MODULE=rbac-structured\nTAG=from-file\nRBAC_PORT=7777\n')
   env={**os.environ,'MODULE':'rbac-ddd','TAG':'explicit','RBAC_PORT':'8888'}
   # Git Bash accepts POSIX-style drive paths; Windows bash.exe resolves Windows paths too.
   path=str(root/'deploy/full-env.sh').replace('\\','/')
   check=root/'check.sh';check.write_text(f'source "{path}"\nprintf "%s %s %s" "$MODULE" "$TAG" "$RBAC_PORT"\n')
   shell=os.environ.get('BASH_TEST_SHELL','bash')
   if os.name=='nt' and 'BASH_TEST_SHELL' not in os.environ:
    found=pathlib.Path(shutil.which('bash') or 'bash')
    candidate=found.parents[2]/'bin/bash.exe' if len(found.parents)>2 else found
    if candidate.exists(): shell=str(candidate)
   run=subprocess.run([shell,str(check).replace('\\','/')],env=env,text=True,encoding='utf-8',errors='replace',capture_output=True)
   self.assertEqual(run.returncode,0,run.stderr);self.assertEqual(run.stdout,'rbac-ddd explicit 8888')
   env['MODULE']='../../foreign'
   run=subprocess.run([shell,str(check).replace('\\','/')],env=env,text=True,encoding='utf-8',errors='replace',capture_output=True)
   self.assertNotEqual(run.returncode,0)
 def test_bootstrap_secret_alias_for_both_backends(self):
  mounted=self.doc['services']['rbac-backend']['secrets']
  targets={item.get('target'):item.get('source') for item in mounted if isinstance(item,dict)}
  self.assertEqual(targets['rbac.bootstrap-password'],'bootstrap-password')
  self.assertEqual(targets['RBAC_BOOTSTRAP_PASSWORD'],'bootstrap-password')
 def test_junit_http_coverage_then_original_verify_order(self):
  for name in ['deploy/full-build.sh','.github/workflows/ci.yml']:
   script=(ROOT/name).read_text(encoding='utf8')
   package=script.index('clean package')
   agent=script.index('source deploy/full-jacoco-agent.sh')
   http=script.index('npm run acceptance')
   gate=script.index('verify -DskipTests')
   self.assertLess(package,agent);self.assertLess(agent,http);self.assertLess(http,gate)
   self.assertNotIn('clean verify',script)
   self.assertNotRegex(script[http:],r'mvnw[^\n]*\bclean\b')
   if name.endswith('ci.yml'):
    self.assertIn('ACCEPTANCE_JACOCO_AGENT=%s',script)
    self.assertIn('ACCEPTANCE_JACOCO_CLI=%s',script)
 def test_ci_names_only_its_own_mysql_and_redis_for_real_restart_guards(self):
  script=(ROOT/'.github/workflows/ci.yml').read_text(encoding='utf8')
  for service in ['mysql','redis']:
   self.assertIn("docker rename '${{ job.services."+service+".id }}' rbac-phase2-test-"+service,script)
  services=yaml.safe_load(script)['jobs']['verify']['services']
  self.assertEqual(services['mysql']['ports'],['127.0.0.1:13306:3306'])
  self.assertEqual(services['redis']['ports'],['127.0.0.1:16379:6379'])
  self.assertIn('rbac-web/test-results/**/*.png',script)
  self.assertNotIn('rbac-web/test-results/\n',script)  # raw traces/DOM contexts may contain temporary credentials
 def test_windows_secrets_use_effective_per_file_acl(self):
  script=(ROOT/'deploy/full-secrets.sh').read_text(encoding='utf8')
  self.assertIn('"${USERNAME}:F"',script)
  self.assertNotIn('/T >',script)
  self.assertIn('protect_runtime\nfor name',script)
  self.assertNotIn('value=$(tr',script)
 def test_final_local_gate_precedes_images_and_never_cleans_targets(self):
  script=(ROOT/'deploy/full-final.sh').read_text(encoding='utf8')
  self.assertLess(script.index('python deploy/full-final-gate.py'),script.index('docker build'))
  self.assertNotIn('mvnw',script);self.assertNotIn('taskkill',script)
  self.assertIn('for module in rbac-structured rbac-ddd;',script)
  self.assertIn('samePersistentVolumes',script)
  self.assertIn('COPY ${MODULE}/target/${MODULE}.jar', (ROOT/'Dockerfile').read_text(encoding='utf8'))
  self.assertNotIn('target/*.jar',(ROOT/'deploy/full-course-backend.Dockerfile').read_text(encoding='utf8'))
 def test_final_local_gate_rejects_failed_or_incomplete_shared_report(self):
  with tempfile.TemporaryDirectory() as tmp:
   import json
   shared=pathlib.Path(tmp)/'shared.json';reactor=pathlib.Path(tmp)/'reactor.log';out=pathlib.Path(tmp)/'proof.json'
   shared.write_text(json.dumps({'exitCode':2,'summary':{'pass':25,'fail':2},'rows':[]}))
   reactor.write_text('BUILD SUCCESS')
   run=subprocess.run(['python',str(ROOT/'deploy/full-final-gate.py'),'--shared-report',str(shared),'--reactor-log',str(reactor),'--output',str(out)],capture_output=True,text=True,encoding='utf-8',errors='replace')
   self.assertNotEqual(run.returncode,0);self.assertFalse(out.exists())
 def test_no_prune_or_volume_delete_and_controlled_switch(self):
  script=(ROOT/'deploy/full-stack.sh').read_text(encoding='utf8')
  self.assertNotIn('docker volume rm',script);self.assertNotIn('docker system prune',script)
  self.assertIn('CONFIRM_STOP_WRITES',script);self.assertIn('service scale',script)
  self.assertIn('npipe:*|unix:*',script)
if __name__=='__main__': unittest.main(verbosity=2)
