// No center/gateway/course stubs; starts immutable private snapshots of the actual three fat jars.
import mysql from 'mysql2/promise';
import assert from 'node:assert/strict';
import {spawn,execFileSync} from 'node:child_process';
import {createWriteStream} from 'node:fs';
import {readFile,writeFile,mkdtemp,copyFile,rm} from 'node:fs/promises';
import {createHash,generateKeyPairSync,randomBytes,randomUUID} from 'node:crypto';
import {tmpdir} from 'node:os';
import {resolve,join} from 'node:path';
import net from 'node:net';
import {hostEnvironment,runtimeEnvironment,guardDatabase} from './real-environment.mjs';
import {provisionAccounts} from './real-provision.mjs';
import {runSix} from './live-six-api.mjs';
const ROOT=resolve(import.meta.dirname,'../..');
const args=process.argv.slice(2);const selected=args.find(x=>x.startsWith('--module='))?.split('=')[1]||'both';
assert(['both','structured','ddd'].includes(selected)&&args.every(x=>x.startsWith('--module=')),'Usage: node run-real-centers.mjs [--module=both|structured|ddd]');
const javaHome=process.env.JAVA_HOME;assert(javaHome,'Explicit JAVA_HOME JDK21 required');
const release=await readFile(join(javaHome,'release'),'utf8');assert(/JAVA_VERSION="21[."]/.test(release),'Strict JDK21 required');
const java=join(javaHome,'bin',process.platform==='win32'?'java.exe':'java');
execFileSync(java,['-version'],{env:hostEnvironment(process.env),stdio:['ignore','pipe','pipe']});
const passwords={center:process.env.REAL_CENTER_DB_PASSWORD,course:process.env.REAL_COURSE_DB_PASSWORD};
assert(Object.values(passwords).every(x=>typeof x==='string'&&x.length),'Explicit dedicated center/course MySQL passwords required');
const output=await mkdtemp(join(tmpdir(),'m6-course-real-'));const report={format:'m6-real-centers-gateway-course-v1',startedAt:new Date().toISOString(),directory:output,upstream:'fde08d5dd417abc543145aff33ca8378deb9ca36',modules:{}};
let interrupted=false;for(const sig of ['SIGINT','SIGTERM'])process.on(sig,()=>{interrupted=true});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));const hash=b=>createHash('sha256').update(b).digest('hex');
async function port(){return new Promise((ok,no)=>{const s=net.createServer();s.once('error',no);s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>ok(p))})})}
async function redis(args){return new Promise((ok,no)=>{const s=net.connect({host:'127.0.0.1',port:16379});let raw=Buffer.alloc(0);const fail=()=>{s.destroy();no(Error('Owned namespace Redis communication failed'))};s.setTimeout(3000);s.on('error',fail);s.on('timeout',fail);s.on('connect',()=>s.write('*'+args.length+'\r\n'+args.map(x=>'$'+Buffer.byteLength(String(x))+'\r\n'+x+'\r\n').join('')));s.on('data',async b=>{raw=Buffer.concat([raw,b]);try{const {parseResp}=await import('../../tools/acceptance/redis.mjs');const parsed=parseResp(raw);if(parsed){s.destroy();ok(parsed.value)}}catch{fail()}})})}
async function cleanRedis(namespace){assert(/^m6_course_real_center_(structured|ddd)_\d{13}_[a-f0-9]{8}$/.test(namespace));let cursor='0';do{const data=await redis(['SCAN',cursor,'MATCH',namespace+':*','COUNT',200]);cursor=data[0];const keys=data[1];assert(keys.every(x=>x.startsWith(namespace+':')));if(keys.length)await redis(['DEL',...keys])}while(cursor!=='0')}
async function run(module){
 const suffix=module+'_'+Date.now()+'_'+randomBytes(4).toString('hex');const names={center:'m6_course_real_center_'+suffix,course:'m6_course_real_course_'+suffix};
 const result={startedAt:new Date().toISOString(),databases:names,ports:{},jars:{},rows:[],cleanup:{children:false,databases:false,redis:false,secrets:false}};report.modules[module]=result;
 const children=[],streams=[],created=new Set(),admins={};let courseDb;const secretFiles=[];
 async function snapshot(tag,path){const source=resolve(path),candidate=join(output,suffix+'-'+tag+'.jar');const before=hash(await readFile(source));await copyFile(source,candidate);const candidateHash=hash(await readFile(candidate));const after=hash(await readFile(source));assert(before===candidateHash&&after===before,'Jar changed while copying; retry after Maven publication');
  const jarTool=join(javaHome,'bin',process.platform==='win32'?'jar.exe':'jar');const entries=execFileSync(jarTool,['tf',candidate],{env:hostEnvironment(process.env),encoding:'utf8',maxBuffer:16*1024*1024});assert(entries.includes('BOOT-INF/classes/'),'Actual executable Spring Boot fat jar required: '+tag);result.jars[tag]={source,sourceSHA256:before,candidateSHA256:candidateHash};secretFiles.push(candidate);return candidate;
 }
 async function start(tag,jar,config){const logPath=join(output,suffix+'-'+tag+'.log');const stream=createWriteStream(logPath,{mode:0o600});streams.push(stream);const child=spawn(java,['-Xms64m','-Xmx384m','-jar',jar,'--spring.config.location=classpath:/'],{cwd:output,env:runtimeEnvironment(process.env,{javaHome,...config}),stdio:['ignore','pipe','pipe']});children.push(child);child.stdout.pipe(stream);child.stderr.pipe(stream);let spawnFailed=false;child.on('error',()=>{spawnFailed=true});result.ports[tag]=config.port;
  const endpoint=config.kind==='course'?'/actuator/health':config.kind==='center'?'/api/v1/actuator/health/readiness':'/api/v1/actuator/health';const until=Date.now()+240000;
  while(Date.now()<until){assert(!interrupted,'Interrupted');if(spawnFailed||child.exitCode!==null)throw Error('Owned '+tag+' startup failed; inspect external log');try{const r=await fetch('http://127.0.0.1:'+config.port+endpoint,{redirect:'error',signal:AbortSignal.timeout(2000)});if(r.ok)return;}catch{}await sleep(500)}throw Error('Owned '+tag+' readiness timeout; inspect external log');
 }
 try{
  const jars={center:await snapshot('center',process.env[module==='structured'?'REAL_STRUCTURED_JAR':'REAL_DDD_JAR']||join(ROOT,`rbac-${module}/target/rbac-${module}.jar`)),gateway:await snapshot('gateway',process.env.REAL_GATEWAY_JAR||join(ROOT,'rbac-gateway/target/rbac-gateway.jar')),course:await snapshot('course',process.env.REAL_COURSE_JAR||join(ROOT,'.cache/course-source/prepared/backend/target/course-project-backend-0.0.1-SNAPSHOT.jar'))};
  assert.equal(await redis(['PING']),'PONG');
  for(const kind of ['center','course']){const dbPort=kind==='center'?13306:13316;const quoted=guardDatabase(kind,dbPort,names[kind]);admins[kind]=await mysql.createConnection({host:'127.0.0.1',port:dbPort,user:'root',password:passwords[kind],timezone:'Z'});await admins[kind].query('CREATE DATABASE '+quoted+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');created.add(kind);}
  const bootstrap='M6!'+randomBytes(20).toString('hex')+'a1',serviceKey=randomBytes(32).toString('hex'),issuer='urn:m6:real:'+suffix,kid='m6-'+randomBytes(8).toString('hex');
  const pair=generateKeyPairSync('rsa',{modulusLength:2048,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});const privateFile=join(output,suffix+'-private.pem'),publicFile=join(output,suffix+'-public.pem');secretFiles.push(privateFile,publicFile);await writeFile(privateFile,pair.privateKey,{mode:0o600});await writeFile(publicFile,pair.publicKey,{mode:0o600});
  const centerPort=await port(),center='http://127.0.0.1:'+centerPort;
  const jdbc=kind=>`jdbc:mysql://127.0.0.1:${kind==='center'?13306:13316}/${names[kind]}?serverTimezone=UTC&allowPublicKeyRetrieval=true&sslMode=DISABLED&connectTimeout=10000&socketTimeout=120000`;
  await start('center',jars.center,{kind:'center',port:centerPort,url:jdbc('center'),dbUser:'root',dbPassword:passwords.center,namespace:names.center,privateFile,publicFile,bootstrap,serviceKey,kid,issuer,strategy:module});
  // Health can be UP before ApplicationRunner bootstrap completes. Read only this run's new schema.
  let bootstrapped=false;for(let n=0;n<180;n++){const [[row]]=await admins.center.query('SELECT password_hash<>? ready FROM '+guardDatabase('center',13306,names.center)+'.sys_user WHERE username=?',['UNINITIALIZED','root']);if(row?.ready){bootstrapped=true;break}await sleep(1000)}assert(bootstrapped,'Controlled root bootstrap did not complete');
  const accounts=await provisionAccounts(center,{bootstrap,serviceKey});result.authorities=accounts.authorityEvidence;result.provisionHTTP=accounts.http;
  const coursePort=await port(),course='http://127.0.0.1:'+coursePort;
  await start('course',jars.course,{kind:'course',port:coursePort,url:jdbc('course'),dbUser:'root',dbPassword:passwords.course,center,issuer,serviceKey,strategy:module});
  courseDb=await mysql.createConnection({host:'127.0.0.1',port:13316,user:'root',password:passwords.course,database:names.course,timezone:'Z',multipleStatements:true});
  let fixture=await readFile(join(import.meta.dirname,'fixture.sql'),'utf8');const map={'m6-teacher-leader':'LEADER','m6-teacher-cc':'CC_TEACHER','m6-teacher-no-cc':'NO_CC_TEACHER','m6-student-leader':'STUDENT_LEADER','m6-student-applicant':'STUDENT_APPLICANT'};
  for(const [synthetic,who]of Object.entries(map))fixture=fixture.replaceAll("'"+synthetic+"'",courseDb.escape(accounts.subjects[who]));await courseDb.query(fixture);
  const [teacher]=await courseDb.query('SELECT user_id,id FROM teacher WHERE user_id=?',[accounts.subjects.LEADER]);assert.equal(teacher.length,1);const [student]=await courseDb.query('SELECT user_id,id FROM student WHERE user_id=?',[accounts.subjects.STUDENT_LEADER]);assert.equal(Number(student[0].id),610001);result.subjectMappings={leader:{subject:teacher[0].user_id,teacherId:teacher[0].id},studentLeader:{subject:student[0].user_id,studentId:student[0].id}};
  const gatewayPort=await port();await start('gateway',jars.gateway,{kind:'gateway',port:gatewayPort,center,course,issuer});
  result.rows=await runSix({origin:'http://127.0.0.1:'+gatewayPort,prefix:'',tokens:accounts.tokens,subjects:accounts.subjects,record:row=>console.log(module+' '+row.name+' '+(row.passed?'PASS':'FAIL '+row.error))});
  if(result.rows.some(x=>!x.passed)) {
   result.diagnostics=[];
   async function diagnostic(origin,path,who,method='GET',body) {const r=await fetch(origin+path,{redirect:'error',signal:AbortSignal.timeout(30000),method,headers:{Authorization:'Bearer '+accounts.tokens[who],'X-Audit-Service-Key':serviceKey,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});let v;try{v=await r.json()}catch{}result.diagnostics.push({target:origin===center?'center':'course',method,path,status:r.status,code:v?.code??null,...(v?.allowed===undefined?{}:{allowed:v.allowed}),...(v?.authorities?{authorities:v.authorities}:{})});}
   await diagnostic(center,'/auth/api-permissions/createTerm','ADMIN');await diagnostic(center,'/integration/authorities','ADMIN');
   await diagnostic(course,'/terms','ADMIN','POST',{name:'M6 direct diagnostic '+Date.now(),startDate:'2026-01-01',endDate:'2036-12-31'});
   await diagnostic(center,'/auth/audit-logs','LEADER','POST',{requestId:randomUUID(),type:'score_changed',objectType:'oral_record',objectId:'cpm-oral_record-610001',target:'M6 failed transport schema diagnostic',before:{score:70},after:{operationId:'updateOralRecord',result:{score:88},courseId:'cpm-course-610001'}});
  }
  const [[score]]=await courseDb.query('SELECT group_score FROM oral_assessment WHERE id=610001');const [[member]]=await courseDb.query('SELECT COUNT(*) n FROM group_membership WHERE group_id=610001 AND student_id=610002');result.persisted={score:Number(score.group_score),approvedMemberships:member.n};
  assert.equal(result.persisted.score,88);assert.equal(result.persisted.approvedMemberships,1);result.passed=result.rows.length===6&&result.rows.every(x=>x.passed);
 }catch(e){result.passed=false;result.error=e.name==='AssertionError'?e.message:'Real fixture/runtime failed; inspect external component logs (secret-bearing exception withheld)';console.log(module+' FAILED '+result.error);
 }finally{
  let allStopped=true;for(const child of children.reverse()){if(child.pid&&child.exitCode===null&&child.signalCode===null){const exited=new Promise(r=>child.once('exit',r));child.kill('SIGTERM');await Promise.race([exited,sleep(10000)]);if(child.exitCode===null&&child.signalCode===null){child.kill('SIGKILL');await Promise.race([exited,sleep(10000)]);}}if(child.pid&&child.exitCode===null&&child.signalCode===null)allStopped=false;}
  result.cleanup.children=allStopped;for(const stream of streams)stream.end();
  if(courseDb)await courseDb.end().catch(()=>{});
  if(allStopped){try{await cleanRedis(names.center);result.cleanup.redis=true}catch{result.cleanup.redis=false}
   let dropped=true;for(const kind of created){try{const dbPort=kind==='center'?13306:13316;await admins[kind].query('DROP DATABASE '+guardDatabase(kind,dbPort,names[kind]));const [rows]=await admins[kind].query('SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?',[names[kind]]);assert.equal(rows.length,0)}catch{dropped=false}}result.cleanup.databases=dropped;
  }
  for(const connection of Object.values(admins))await connection.end().catch(()=>{});
  let removed=true;for(const path of secretFiles){try{await rm(path,{force:true})}catch{removed=false}}result.cleanup.secrets=removed;result.finishedAt=new Date().toISOString();
 }
}
for(const module of selected==='both'?['structured','ddd']:[selected]){if(interrupted)break;await run(module);await writeFile(join(output,'report.json'),JSON.stringify(report,null,2));}
report.finishedAt=new Date().toISOString();report.passed=!interrupted&&Object.keys(report.modules).length===(selected==='both'?2:1)&&Object.values(report.modules).every(x=>x.passed&&Object.values(x.cleanup).every(Boolean));await writeFile(join(output,'report.json'),JSON.stringify(report,null,2));console.log('Report: '+join(output,'report.json'));process.exitCode=report.passed?0:1;
