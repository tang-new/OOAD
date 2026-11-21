"""Record only the isolated stack's controlled stop-write barrier; never read environments/secrets."""
import argparse,datetime,json,os,pathlib,subprocess
APPLICATIONS=['rbac-web','course-web','course-backend','rbac-backend']
DATA=['mysql','redis']
def run(*args):return subprocess.check_output(['docker',*args],text=True,encoding='utf-8',errors='replace',timeout=60).strip()
def snapshot(stack):
 rows={}
 for name in APPLICATIONS+DATA:
  service=stack+'_'+name
  ids=run('ps','-q','--filter','label=com.docker.swarm.service.name='+service).splitlines()
  rows[name]={'service':service,'desiredReplicas':int(run('service','inspect','--format','{{.Spec.Mode.Replicated.Replicas}}',service)),'configuredImage':run('service','inspect','--format','{{.Spec.TaskTemplate.ContainerSpec.Image}}',service),'containers':[{'containerId':run('inspect','--format','{{.Id}}',i),'imageId':run('inspect','--format','{{.Image}}',i)} for i in ids]}
 return rows
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--phase',choices=['before','stopped'],required=True);p.add_argument('--file',type=pathlib.Path,required=True);a=p.parse_args()
 stack=os.environ.get('STACK')
 if stack!='rbac1-acceptance':raise SystemExit('Only isolated stack authorized')
 rows=snapshot(stack);now=datetime.datetime.now(datetime.timezone.utc).isoformat()
 if a.phase=='before':
  evidence={'stack':stack,'nextModule':os.environ['MODULE'],'nextTag':os.environ['TAG'],'before':{'at':now,'services':rows}}
 else:
  evidence=json.loads(a.file.read_text(encoding='utf-8'))
  if any(rows[n]['containers'] or rows[n]['desiredReplicas']!=0 for n in APPLICATIONS):raise SystemExit('Old application task still running/desired at stop-write barrier')
  for name in DATA:
   if rows[name]['containers']!=evidence['before']['services'][name]['containers']:raise SystemExit('Data service unexpectedly restarted during stop-write barrier: '+name)
  evidence['stopped']={'at':now,'services':rows,'allFiveApplicationServicesStopped':True,'sameMySqlRedisContainers':True}
 a.file.parent.mkdir(parents=True,exist_ok=True);a.file.write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
 print('Switch evidence '+a.phase+': '+str(a.file))
if __name__=='__main__':main()
