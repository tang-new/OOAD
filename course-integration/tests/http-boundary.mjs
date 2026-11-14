// Real teacher executable + real dedicated MySQL. Only the central HTTP boundary is simulated.
// No CourseService/repository mocks. Requires Node 22, Docker, JDK21, built patched jar.
import {createServer} from 'node:http';
import {generateKeyPairSync,sign,randomUUID} from 'node:crypto';
import {spawn,spawnSync} from 'node:child_process';
import {readFileSync,createWriteStream} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
const here=dirname(fileURLToPath(import.meta.url));
const jar=process.env.COURSE_JAR||resolve(here,'../../.cache/course-source/prepared/backend/target/course-project-backend-0.0.1-SNAPSHOT.jar');
const logPath=process.env.COURSE_LOG;assert(logPath,'Set COURSE_LOG outside the repository');
const log=createWriteStream(logPath);
const db='course_integration_boundary';
function sql(text,database=db){const r=spawnSync('docker',['exec','-i','m6-course-mysql','mysql','-uroot','-pm6-course-local-only','--batch',...(database?[database]:[])],{input:text,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout;}
// This one database is disposable and exclusively owned by this test. Never accept an arbitrary DB name.
sql(`DROP DATABASE IF EXISTS ${db}; CREATE DATABASE ${db}; GRANT ALL ON ${db}.* TO 'course_integration'@'%';`,'');
const issuer='http://127.0.0.1:18081';const key='m6-boundary-backend-key';
const {privateKey,publicKey}=generateKeyPairSync('rsa',{modulusLength:2048});
const jwk={...publicKey.export({format:'jwk'}),kid:'m6',use:'sig',alg:'RS256'};
function jwt(subject,extra={}){const enc=x=>Buffer.from(JSON.stringify(x)).toString('base64url');const data=enc({alg:'RS256',kid:'m6',typ:'JWT'})+'.'+enc({iss:issuer,sub:subject,aud:['course-project-management'],iat:Math.floor(Date.now()/1000),exp:Math.floor(Date.now()/1000)+3600,...extra});return data+'.'+sign('RSA-SHA256',Buffer.from(data),privateKey).toString('base64url');}
function subject(req){try{return JSON.parse(Buffer.from(req.headers.authorization.split('.')[1],'base64url')).sub}catch{return null}}
const authorities={
 'm6-admin':['ROLE_admin','TC','TE','TD','TCM','CR','CV','AV'],
 'm6-teacher-leader':['ROLE_teacher'],
 'm6-teacher-cc':['ROLE_teacher','CC'],
 'm6-teacher-no-cc':['ROLE_teacher'],
 'm6-student-leader':['ROLE_student'],
 'm6-student-applicant':['ROLE_student']
};
let denied=false,failAudit=false,timeoutOnce=false;let decisions=0,authorityCalls=0,writeAttempts=[];const events=new Map();
const center=createServer(async(req,res)=>{
 const url=new URL(req.url,issuer);let data;
 if(url.pathname==='/.well-known/jwks.json')data={keys:[jwk]};
 else if(url.pathname.startsWith('/auth/api-permissions/')){decisions++;data={allowed:!denied};}
 else if(url.pathname==='/integration/authorities'){
  assert.equal(req.headers['x-audit-service-key'],key);authorityCalls++;data={authorities:authorities[subject(req)]||[]};
 }else if(url.pathname==='/auth/audit-logs'){
  assert.equal(req.headers['x-audit-service-key'],key);
  if(req.method==='POST'){
   let raw='';for await(const chunk of req)raw+=chunk;const body=JSON.parse(raw);writeAttempts.push(body);
   if(failAudit){res.writeHead(503).end();return;}
   let event=events.get(body.requestId);
   if(!event){event={id:events.size+1,at:new Date().toISOString(),actor:{id:1,name:'Synthetic',roles:['teacher'],deleted:false},...body};events.set(body.requestId,event);}
   if(timeoutOnce){timeoutOnce=false;setTimeout(()=>res.destroy(),6500);return;}
   data=event;
  }else data={page:1,pageSize:100,total:events.size,items:[...events.values()]};
 }else {res.writeHead(404).end();return;}
 res.setHeader('Content-Type','application/json');res.end(JSON.stringify(data));
});
await new Promise(r=>center.listen(18081,'127.0.0.1',r));
const java=process.env.JAVA_HOME?resolve(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const backend=spawn(java,['-jar',jar],{env:{...process.env,SPRING_PROFILES_ACTIVE:'integration',SERVER_PORT:'18080',DB_HOST:'127.0.0.1',DB_PORT:'13316',DB_NAME:db,DB_USER:'course_integration',DB_PASSWORD:'m6-course-local-only',IDENTITY_BASE_URL:issuer,IDENTITY_ISSUER:issuer,IDENTITY_JWK_SET_URI:issuer+'/.well-known/jwks.json',AUDIT_SERVICE_KEY:key,SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:'2',APP_BACKEND_STRATEGY:process.env.COURSE_STRATEGY||'structured'}});
backend.stdout.pipe(log);backend.stderr.pipe(log);
const origin='http://127.0.0.1:18080';
async function request(path,{subject='m6-admin',method='GET',body,status=200,token,headers={}}={}){
 const r=await fetch(origin+path,{redirect:'error',method,headers:{Authorization:'Bearer '+(token||jwt(subject)),'Content-Type':'application/json',...headers},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const text=await r.text();console.log(method,path,r.status,'audit='+r.headers.get('x-audit-state'),'requestId='+r.headers.get('x-request-id'));assert.equal(r.status,status,`${method} ${path}: ${text}`);try{return JSON.parse(text)}catch{return text}
}
try{
 let ready=false;for(let n=0;n<180;n++){try{const r=await fetch(origin+'/actuator/health',{redirect:'error'});if(r.ok){ready=true;break}}catch{}if(backend.exitCode!==null)throw new Error('Backend exited; inspect '+logPath);await new Promise(r=>setTimeout(r,1000));}assert(ready,'Backend not healthy; inspect '+logPath);
 sql(readFileSync(resolve(here,'fixture.sql'),'utf8'));
 // Local trust boundary: forged roles, audience, signature, issuer, and expiration cannot add authorities.
 const beforeDecisions=decisions;await request('/terms',{token:jwt('m6-admin',{aud:['other-system']}),status:401});assert.equal(decisions,beforeDecisions);
 await request('/terms',{token:jwt('m6-admin',{iss:'http://evil.invalid'}),status:401});
 await request('/terms',{token:jwt('m6-admin',{exp:1}),status:401});
 await request('/terms',{token:jwt('m6-admin').slice(0,-8)+'AAAAAAAA',status:401});
 denied=true;const beforeAuthorities=authorityCalls;await request('/terms',{status:403});
 await request('/terms',{method:'POST',status:403,body:{name:'M6-DENIED-MUST-NOT-EXIST',startDate:'2026-01-01',endDate:'2036-12-31'},headers:{'X-Operation-ID':'listTerms'}});
 assert.equal(authorityCalls,beforeAuthorities);assert(sql("SELECT COUNT(*) FROM term WHERE name='M6-DENIED-MUST-NOT-EXIST';").includes('0'));denied=false;
 const me=await request('/me',{subject:'m6-teacher-leader'});assert.equal(me.userId,'m6-teacher-leader');assert(me.teacherId,'subject must map to actual Teacher');
 const student=await request('/me',{subject:'m6-student-leader'});assert.equal(student.studentId,610001);
 // 1. Real term management; use unique values, no static fixture response.
 const term=await request('/terms',{method:'POST',status:201,body:{name:'M6 HTTP '+Date.now(),startDate:'2026-01-01',endDate:'2036-12-31'}});assert(term.id);
 // 2+3. Real teacher submission and independent administrator course review.
 const course=await request(`/terms/${term.id}/courses`,{subject:'m6-teacher-leader',method:'POST',status:201,body:{name:'M6 HTTP Course',code:'M6-'+Date.now(),workMode:'group',description:''}});assert(course.id);
 await request(`/courses/${course.id}/submit-review`,{subject:'m6-teacher-leader',method:'POST'});
 await request(`/courses/${course.id}/review`,{method:'POST',body:{decision:'approved'}});
 // 4. allowed=true is NOT CC. Teacher first, leader exception, real CC branch.
 const copy=subject=>({subject,method:'POST',status:201,body:{sourceCourseId:610001,targetTermId:term.id,code:'M6-COPY-'+randomUUID().slice(0,8)}});
 await request('/courses/copy',copy('m6-teacher-leader'));
 await request('/courses/copy',copy('m6-teacher-cc'));
 await request('/courses/copy',{...copy('m6-teacher-no-cc'),status:403,token:jwt('m6-teacher-no-cc',{roles:['admin'],authorities:['CC','ROLE_teacher']})});
 await request('/courses/copy',{...copy('m6-admin'),status:403});
 // 5. Actual student leader approves a real pending join request, membership is persisted.
 await request('/join-requests/610001',{subject:'m6-student-leader',method:'PATCH',body:{decision:'approved'},headers:{'X-Audit-Service-Key':'attacker-cannot-forward'}});
 assert(sql('SELECT COUNT(*) FROM group_membership WHERE group_id=610001 AND student_id=610002;').includes('1'));
 // 6. Real oral grade mutation; timeout after central commit retries same UUID/body.
 timeoutOnce=true;const id=randomUUID();
 await request('/oral-records/610001',{subject:'m6-teacher-leader',method:'PATCH',body:{verdict:'passed',groupScore:88,memo:'Synthetic boundary',personalScores:[{studentId:610001,score:89},{studentId:610002,score:87}]},headers:{'X-Request-ID':id,'X-Audit-Service-Key':'untrusted'}});
 const attempts=writeAttempts.filter(x=>x.requestId===id);assert.equal(attempts.length,2);assert.deepEqual(attempts[0],attempts[1]);assert.equal(events.get(id).before.score,70);assert.equal(events.get(id).after.score,88);
 assert(sql('SELECT group_score FROM oral_assessment WHERE id=610001;').includes('88'));
 const logs=await request('/audit-logs',{subject:'m6-teacher-leader'});assert(logs.total>=2,'Real GET audit transport + course visibility must work');
 failAudit=true;await request('/oral-records/610001',{subject:'m6-teacher-leader',method:'PATCH',body:{verdict:'passed',groupScore:91,personalScores:[{studentId:610001,score:91},{studentId:610002,score:91}]},status:503});assert(sql('SELECT group_score FROM oral_assessment WHERE id=610001;').includes('91'),'Explicitly demonstrate post-commit unconfirmed window');
 console.log(JSON.stringify({passed:true,strategy:process.env.COURSE_STRATEGY||'structured',classes:6,decisions,authorityCalls,auditEvents:events.size,timeoutIdempotency:true,unconfirmedWindowDemonstrated:true}));
}finally{backend.kill();center.closeAllConnections();await new Promise(r=>center.close(r));log.end();}
