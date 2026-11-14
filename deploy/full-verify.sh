#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/full-env.sh"
export EVIDENCE_FILE=${EVIDENCE_FILE:-$ROOT/deploy/deployment-evidence.json}
# Runtime inspection only, never container environments or secret contents.
python - <<'PY'
import json,os,subprocess,time,urllib.request,urllib.error,ssl,socket,hashlib
from pathlib import Path
stack=os.environ['STACK']
def run(*args): return subprocess.check_output(['docker',*args],text=True,encoding='utf-8',errors='replace',timeout=90).strip()
names=['mysql','redis','rbac-backend','rbac-gateway','rbac-web','course-backend','course-web']
report={'format':'rbac1-local-swarm-v1','module':os.environ['MODULE'],'stack':stack,'timestamp':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'tls':'self-signed local acceptance only; NOT public trust','stage':os.environ.get('DEPLOYMENT_STAGE','candidate'),'finalAcceptance':False,'verificationScope':'Seven-service runtime health, local TLS, private-route denial, published-port isolation and exact image identity; not final business acceptance','status':'failed','services':[],'probes':[]}
error=None
try:
 report['docker']=json.loads(run('info','--format','{{json .Swarm}}'))
 # Remove manager addresses; no cluster joining credentials requested anywhere.
 report['docker'].pop('RemoteManagers',None)
 report['nodes']=[json.loads(x) for x in run('node','ls','--format','{{json .}}').splitlines()]
 for attempt in range(120):
  rows=[]
  for name in names:
   service=stack+'_'+name
   spec=json.loads(run('service','inspect',service))[0]
   tasks=[json.loads(x) for x in run('service','ps','--no-trunc','--filter','desired-state=running','--format','{{json .}}',service).splitlines()]
   container=spec['Spec']['TaskTemplate']['ContainerSpec']
   rows.append({'name':service,'image':container['Image'],'replicas':spec['Spec']['Mode']['Replicated']['Replicas'],'ports':spec.get('Endpoint',{}).get('Ports',[]),'secretTargets':[item.get('File',{}).get('Name') for item in container.get('Secrets',[])],'tasks':tasks})
  report['services']=rows
  if all(len(r['tasks'])==1 and r['tasks'][0]['CurrentState'].startswith('Running') for r in rows): break
  time.sleep(5)
 else: raise RuntimeError('All seven Swarm services did not converge')
 for r in rows:
  name=r['name'].split('_',1)[1]
  if name not in ('rbac-web','course-web') and r['ports']: raise RuntimeError('Internal service publishes ports')
  ids=run('ps','-q','--filter','label=com.docker.swarm.service.name='+r['name']).splitlines()
  if len(ids)!=1: raise RuntimeError('Expected exactly one owned local container for '+r['name'])
  r['containerId']=run('inspect','--format','{{.Id}}',ids[0])
  r['imageId']=run('inspect','--format','{{.Image}}',ids[0])
  r['expectedLocalImageId']=run('image','inspect','--format','{{.Id}}',r['image'])
  r['imageMatchesLocalBuild']=r['imageId']==r['expectedLocalImageId']
  if not r['imageMatchesLocalBuild']: raise RuntimeError('Running image differs from the current local image: '+r['name']+'; use a new immutable TAG to redeploy')
  if name=='rbac-backend' and not r['image'].startswith(os.environ.get('REGISTRY','')+os.environ['MODULE']+':'): raise RuntimeError('Selected MODULE differs from running center image; set MODULE to the actual deployed backend')
  if name!='rbac-backend' and 'jwt-private.pem' in r['secretTargets']: raise RuntimeError('Private signing key mounted outside center')
  if name=='rbac-backend' and not {'rbac.bootstrap-password','RBAC_BOOTSTRAP_PASSWORD'}.issubset(r['secretTargets']): raise RuntimeError('Missing bootstrap secret alias')
  if name=='mysql':
   mounts=json.loads(run('inspect','--format','{{json .Mounts}}',ids[0]))
   r['persistentVolumes']=[{'name':m.get('Name'),'destination':m.get('Destination')} for m in mounts if m.get('Type')=='volume']
   if not any(m['name']==stack+'_mysql-data' and m['destination']=='/var/lib/mysql' for m in r['persistentVolumes']): raise RuntimeError('Unexpected MySQL persistent volume')
   r['health']=run('inspect','--format','{{.State.Health.Status}}',ids[0])
   if r['health']!='healthy': raise RuntimeError('MySQL container health is not healthy')
  if name=='redis':
   r['ping']=run('exec',ids[0],'redis-cli','ping')
   if r['ping']!='PONG': raise RuntimeError('Redis PING failed')
 # Probe real internal backends from the owned course edge task, never publish their ports.
 edge=run('ps','-q','--filter','label=com.docker.swarm.service.name='+stack+'_course-web').splitlines()
 if len(edge)!=1: raise RuntimeError('Expected one local owned course edge task')
 for url in ['http://course-backend:'+os.environ['COURSE_PORT']+'/actuator/health','http://rbac-gateway:'+os.environ['GATEWAY_PORT']+'/api/v1/actuator/health']:
  for attempt in range(90):
   probe=subprocess.run(['docker','exec',edge[0],'wget','-q','-O','-','--timeout=5',url],capture_output=True,text=True)
   try: healthy=probe.returncode==0 and json.loads(probe.stdout).get('status')=='UP'
   except (ValueError,AttributeError): healthy=False
   if healthy: break
   time.sleep(2)
  report['probes'].append({'url':url,'via':'owned course-web overlay task','passed':healthy})
  if not healthy: raise RuntimeError('Internal health failed: '+url)
 # Direct overlay access still requires JWT; the course Service is not an unauthenticated bypass.
 direct_url='http://course-backend:'+os.environ['COURSE_PORT']+'/api/terms'
 direct=subprocess.run(['docker','exec',edge[0],'wget','-S','-O','/dev/null','--timeout=5',direct_url],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=20)
 import re
 direct_status=re.findall(r'HTTP/[0-9.]+\s+(\d{3})',direct.stderr)
 direct_status=int(direct_status[-1]) if direct_status else 0
 report['probes'].append({'url':direct_url,'via':'owned course-web overlay task','status':direct_status,'expected':401,'passed':direct_status==401})
 if direct_status!=401: raise RuntimeError('Direct course overlay JWT enforcement failed')
 ctx=ssl._create_unverified_context() # Explicitly local self-signed acceptance, never production trust.
 report['tlsEndpoints']=[]
 for port in [os.environ['RBAC_TLS_PORT'],os.environ['COURSE_TLS_PORT']]:
  with socket.create_connection(('localhost',int(port)),timeout=10) as raw:
   with ctx.wrap_socket(raw,server_hostname='localhost') as connection:
    row={'port':int(port),'protocol':connection.version(),'cipher':connection.cipher()[0],'certificateSha256':hashlib.sha256(connection.getpeercert(binary_form=True)).hexdigest(),'trust':'local self-signed, not public trust'}
    report['tlsEndpoints'].append(row)
    if row['protocol'] not in ('TLSv1.2','TLSv1.3'): raise RuntimeError('Insecure TLS protocol')
 for port,path,expected in [(os.environ['RBAC_TLS_PORT'],'/',200),(os.environ['COURSE_TLS_PORT'],'/',200),(os.environ['RBAC_TLS_PORT'],'/api/v1/actuator/health',200),(os.environ['RBAC_TLS_PORT'],'/integration/authorities',403),(os.environ['RBAC_TLS_PORT'],'/identity/users/1',403),(os.environ['COURSE_TLS_PORT'],'/integration/authorities',403),(os.environ['COURSE_TLS_PORT'],'/identity/users/1',403),(os.environ['COURSE_TLS_PORT'],'/api/terms',401)]:
  url='https://localhost:'+port+path
  for attempt in range(60):
   try:
    with urllib.request.urlopen(url,context=ctx,timeout=5) as response: status=response.status; body=response.read(65536).decode(errors='replace')
   except urllib.error.HTTPError as e: status=e.code;body=''
   except OSError: status=0;body=''
   if status==expected:break
   time.sleep(2)
  passed=status==expected
  probe={'url':url,'status':status,'expected':expected,'passed':passed}
  if path.endswith('/actuator/health') and status==200:
   try: probe['health']=json.loads(body).get('status')
   except (ValueError,AttributeError): probe['health']='INVALID_RESPONSE'
   passed=passed and probe['health']=='UP';probe['passed']=passed
  report['probes'].append(probe)
  if not passed: raise RuntimeError('Probe failed: '+url+' status '+str(status))
 report['status']=report['stage']+'-runtime-passed'
except Exception as e: error=str(e);report['error']=error
finally:
 Path(os.environ['EVIDENCE_FILE']).write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
 print('Evidence: '+os.environ['EVIDENCE_FILE']+' status='+report['status'])
if error: raise SystemExit(error)
PY
