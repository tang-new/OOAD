"""Read-only final-local prerequisite gate. Never starts/stops a test JVM or cleans targets."""
import argparse, hashlib, json, pathlib, re, sys, zipfile
import xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def check(shared, reactor):
 report=json.loads(shared.read_text(encoding='utf-8'))
 if report.get('exitCode')!=0 or report.get('summary',{}).get('fail')!=0:
  raise ValueError('Shared report is not green: exitCode/failed rows block final images')
 rows=report.get('rows',[])
 counts={m:sum(r.get('module')==m and r.get('status')=='pass' for r in rows) for m in ['structured','ddd']}
 if any(v<25 for v in counts.values()) or any(r.get('status')!='pass' for r in rows):
  raise ValueError('Both backends must complete at least 25 successful shared scenarios')
 if not any(r.get('module')=='comparison' and r.get('scenario','').startswith('T21') and r.get('status')=='pass' for r in rows):
  raise ValueError('Successful real T21 comparison is required')
 text=re.sub(r'\x1b\[[0-9;]*m','',reactor.read_text(encoding='utf-8',errors='replace'))
 if 'BUILD SUCCESS' not in text or 'BUILD FAILURE' in text:
  raise ValueError('Full reactor verify is not BUILD SUCCESS; do not build final images yet')
 for module in ['rbac-contract','rbac-structured','rbac-ddd','rbac-gateway']:
  if not re.search(r'\b'+re.escape(module)+r'\s+\.{2,}\s+SUCCESS',text):
   raise ValueError('Missing successful full-reactor module: '+module)
 if re.search(r'Tests run:[^\n]*?(?:Failures|Errors):\s*[1-9]',text):
  raise ValueError('Nonzero JUnit failure/error in supplied reactor log')
 # verify -DskipTests is the intended final phase AFTER normal JUnit and HTTP.
 # Require the actual Surefire results too, so skipped execution cannot fake a green gate.
 junit={}
 for module,minimum in {'rbac-contract':2,'rbac-structured':49,'rbac-ddd':112,'rbac-gateway':25}.items():
  files=sorted((ROOT/module/'target/surefire-reports').glob('TEST-*.xml'))
  totals={key:0 for key in ['tests','failures','errors','skipped']}
  for file in files:
   suite=ET.parse(file).getroot()
   for key in totals: totals[key]+=int(suite.get(key,'0'))
  if totals['tests']<minimum or any(totals[key] for key in ['failures','errors','skipped']):
   raise ValueError('Actual normal JUnit execution is missing or not green: '+module)
  junit[module]={**totals,'reportFiles':len(files)}
 artifacts={}
 for name in ['rbac-structured','rbac-ddd','rbac-gateway']:
  path=ROOT/name/'target'/f'{name}.jar'
  with zipfile.ZipFile(path) as archive:
   manifest=archive.read('META-INF/MANIFEST.MF').decode('utf-8')
   if 'Main-Class:' not in manifest or not any(n.startswith('BOOT-INF/lib/') for n in archive.namelist()):
    raise ValueError('Canonical artifact is not an executable Spring Boot jar: '+name)
  actual=digest(path)
  if name in ['rbac-structured','rbac-ddd'] and report.get('modules',{}).get(name.removeprefix('rbac-'),{}).get('jarSha256')!=actual:
   raise ValueError('Canonical artifact differs from the actual tested HTTP snapshot: '+name)
  artifacts[name]={'path':str(path.relative_to(ROOT)).replace('\\','/'),'sha256':actual,'bytes':path.stat().st_size}
 course=ROOT/'.cache/course-source/prepared/backend/target/course-project-backend-0.0.1-SNAPSHOT.jar'
 with zipfile.ZipFile(course) as archive:
  if not any(n.startswith('BOOT-INF/lib/') for n in archive.namelist()): raise ValueError('Course canonical artifact is not runnable')
 artifacts['course-backend']={'path':str(course.relative_to(ROOT)).replace('\\','/'),'sha256':digest(course),'bytes':course.stat().st_size}
 return {'status':'prerequisites-passed','sharedReport':{'file':shared.name,'sha256':digest(shared),'counts':counts,'T21':'pass','exitCode':0},'reactorVerify':{'file':reactor.name,'sha256':digest(reactor),'status':'BUILD SUCCESS','modules':['rbac-contract','rbac-structured','rbac-ddd','rbac-gateway']},'canonicalArtifacts':artifacts,'junit':junit}
def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--shared-report',type=pathlib.Path,required=True);parser.add_argument('--reactor-log',type=pathlib.Path,required=True);parser.add_argument('--output',type=pathlib.Path,required=True)
 args=parser.parse_args()
 try: result=check(args.shared_report,args.reactor_log)
 except (ValueError,OSError,KeyError,zipfile.BadZipFile,json.JSONDecodeError,ET.ParseError) as error:
  print('FINAL LOCAL DEPLOYMENT BLOCKED: '+str(error),file=sys.stderr);return 1
 args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
 print('Final-local prerequisites passed; executable canonical artifacts hashed. No image or deployment performed by this gate.')
 return 0
if __name__=='__main__':raise SystemExit(main())
