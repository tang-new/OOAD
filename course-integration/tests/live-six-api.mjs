// Shared six-class suite. Real tokens only; caller can select a bare gateway or same-origin /api prefix.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {pathToFileURL} from 'node:url';
export async function runSix({origin,prefix='/api',tokens,subjects,record=async()=>{}}) {
 const rows=[];
 async function call(who,path,method='GET',body,status=200,requestId=randomUUID()) {
  const r=await fetch(origin+prefix+path,{redirect:'error',signal:AbortSignal.timeout(45000),method,headers:{Authorization:'Bearer '+tokens[who],'Content-Type':'application/json','X-Request-ID':requestId},...(body===undefined?{}:{body:JSON.stringify(body)})});
  let value;try{value=await r.json()}catch{value=null}
  const observation={method,path,status:r.status,code:value?.code??null};current.push(observation);
  assert.equal(r.status,status,`${method} ${path}: expected ${status}, actual HTTP ${r.status}, code ${value?.code??'none'}`);return value;
 }
 let current=[];async function scenario(name,fn) {current=[];const row={name,passed:false};try{row.result=await fn();row.passed=true}catch(e){row.error=e.name==='AssertionError'?e.message:'Execution/transport failed (secret-bearing exception withheld)'}row.http=current;rows.push(row);await record(row);}
 let term,course;
 await scenario('term-management',async()=>{term=await call('ADMIN','/terms','POST',{name:'M6 real '+Date.now(),startDate:'2026-01-01',endDate:'2036-12-31'},201);assert(term.id);return {termId:term.id}});
 await scenario('teacher-course-submission',async()=>{
  const targetTerm=term?.id??610001;const teacher=await call('LEADER','/me');assert.equal(teacher.userId,subjects.LEADER);assert(teacher.teacherId);
  course=await call('LEADER',`/terms/${targetTerm}/courses`,'POST',{name:'M6 Real Course',code:'M6-REAL-'+Date.now(),workMode:'group',description:''},201);
  await call('LEADER',`/courses/${course.id}/submit-review`,'POST');return {courseId:course.id,teacherId:teacher.teacherId};
 });
 await scenario('independent-course-review',async()=>{assert(course?.id,'Course prerequisite failed');await call('ADMIN',`/courses/${course.id}/review`,'POST',{decision:'approved'});return {courseId:course.id}});
 await scenario('copy-leader-CC-and-teacher-qualification',async()=>{
  const targetTerm=term?.id??610001;for(const who of ['LEADER','CC_TEACHER','NO_CC_TEACHER','ADMIN'])await call(who,'/courses/copy','POST',{sourceCourseId:610001,targetTermId:targetTerm,code:'M6-COPY-'+randomUUID().slice(0,8)},['LEADER','CC_TEACHER'].includes(who)?201:403);
  return {leaderWithoutCC:201,nonleaderWithCC:201,nonleaderWithoutCC:403,adminNotTeacher:403};
 });
 await scenario('student-leader-join-approval',async()=>{const me=await call('STUDENT_LEADER','/me');assert.equal(me.userId,subjects.STUDENT_LEADER);assert.equal(me.studentId,610001);const approved=await call('STUDENT_LEADER','/join-requests/610001','PATCH',{decision:'approved'});assert.equal(approved.state,2);return {studentId:me.studentId,state:approved.state}});
 await scenario('oral-score-audit-POST-GET',async()=>{
  const requestId=randomUUID();await call('LEADER','/oral-records/610001','PATCH',{verdict:'passed',groupScore:88,personalScores:[{studentId:610001,score:89},{studentId:610002,score:87}]},200,requestId);
  const logs=await call('LEADER','/audit-logs');const event=logs.items.find(x=>x.type==='score_changed'&&x.target==='updateOralRecord'&&x.before?.score===70&&x.after?.score===88);
  assert(event,'Actual scoped audit GET must contain real 70->88 score event');return {auditId:event.id,auditRequestId:requestId,before:70,after:88};
 });
 return rows;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
 assert.equal(process.env.COURSE_DEDICATED_DB_CONFIRM,'course_integration','Dedicated integration DB confirmation required');const tokens={},subjects={};
 for(const who of ['ADMIN','LEADER','CC_TEACHER','NO_CC_TEACHER','STUDENT_LEADER']) {tokens[who]=process.env[`COURSE_${who}_TOKEN`];subjects[who]=process.env[`COURSE_${who}_SUBJECT`];assert(tokens[who]&&subjects[who],`Missing explicit token/subject for ${who}`);}
 const rows=await runSix({origin:process.env.COURSE_GATEWAY||'http://localhost:8090',prefix:process.env.COURSE_API_PREFIX??'/api',tokens,subjects,record:row=>console.log(JSON.stringify(row))});process.exitCode=rows.every(x=>x.passed)?0:1;
}
