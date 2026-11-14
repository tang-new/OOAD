import mysql from 'mysql2/promise';
import { generateKeyPairSync, randomBytes, createHash } from 'node:crypto';
import { mkdtemp, mkdir, writeFile, rm, access, readFile, copyFile } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { spawn, execFileSync } from 'node:child_process';
import net from 'node:net';

export const ROOT = resolve(import.meta.dirname, '../..');
import { guardDatabase } from './fixture-guard.mjs';
import { clearOwnedNamespace } from './redis.mjs';
import { applicationEnvironment, hostEnvironment } from './environment.mjs';
export { guardDatabase } from './fixture-guard.mjs';
export async function freePort(port) {
  await new Promise((res, rej) => { const s = net.createServer(); s.once('error', () => rej(new Error(`Port ${port} occupied; never killing foreign processes`))); s.listen(port, '127.0.0.1', () => s.close(res)); });
}
const sleep = ms => new Promise(r => setTimeout(r, ms));
async function availableLoopbackPort() {
  return new Promise((resolvePort,reject)=>{
    const server=net.createServer();server.once('error',reject);
    server.listen(0,'127.0.0.1',()=>{
      const port=server.address().port;
      server.close(error=>error?reject(error):resolvePort(port));
    });
  });
}
export async function provision(module) {
  if (!['structured','ddd'].includes(module)) throw new Error('Invalid module');
  const javaHome = process.env.JAVA_HOME;
  if (!javaHome) throw new Error('JAVA_HOME must explicitly identify JDK21');
  const java = join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  const version = execFileSync(java, ['-version'], {encoding:'utf8', env:hostEnvironment(process.env), stdio:['ignore','pipe','pipe']});
  // java -version normally writes to stderr; use release file for a strict JDK21 check.
  const release = await readFile(join(javaHome, 'release'), 'utf8');
  if (!/JAVA_VERSION="21[.\"]/.test(release)) throw new Error('JAVA_HOME is not JDK21');
  void version;
  const ports = module === 'structured' ? [19081,19181] : [19082,19182];
  for (const p of ports) await freePort(p);
  const jar = resolve(ROOT, `rbac-${module}/target/rbac-${module}.jar`);
  await access(jar);
  const agent=process.env.ACCEPTANCE_JACOCO_AGENT;
  const cli=process.env.ACCEPTANCE_JACOCO_CLI;
  if(agent) {
    if(!cli) throw new Error('Coverage requires ACCEPTANCE_JACOCO_CLI for a verified dump before terminating owned JVMs');
    await access(agent); await access(cli);
  }
  const host = process.env.ACCEPTANCE_DB_HOST ?? '127.0.0.1';
  const port = Number(process.env.ACCEPTANCE_DB_PORT ?? 13306);
  const name = `rbac1_contract_${module}_${Date.now()}_${randomBytes(4).toString('hex')}`;
  const quoted = guardDatabase(host, port, name);
  const user = process.env.ACCEPTANCE_DB_USER ?? 'root';
  const password = process.env.ACCEPTANCE_DB_PASSWORD;
  if (password === undefined) throw new Error('Set ACCEPTANCE_DB_PASSWORD (dedicated local MySQL only)');
  const dir = await mkdtemp(join(tmpdir(), 'rbac1-acceptance-'));
  // Run an immutable snapshot, not target/*.jar: Windows keeps open jars locked
  // and would otherwise make a parallel Maven repackage leave a plain jar.
  const runtimeJar=join(dir,'runtime.jar');
  await copyFile(jar,runtimeJar);
  const jarSha256=createHash('sha256').update(await readFile(runtimeJar)).digest('hex');
  const {privateKey,publicKey} = generateKeyPairSync('rsa', {modulusLength:2048, privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
  const privateFile = join(dir,'private.pem'), publicFile = join(dir,'public.pem');
  await writeFile(privateFile, privateKey, {mode:0o600}); await writeFile(publicFile, publicKey);
  const bootstrap = `Fx!${randomBytes(18).toString('hex')}a1`;
  const serviceKey = randomBytes(32).toString('hex');
  const kid = `fixture-${randomBytes(8).toString('hex')}`, issuer = 'urn:rbac1:contract';
  const namespace = `${name}:`;
  let admin, db, ownedDatabase = false;
  const children = new Map(); const streams = [];
  const coveragePorts=[];
  let dumpSequence=0;
  const applicationConfig={javaHome,url:`jdbc:mysql://${host}:${port}/${name}?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=10000&socketTimeout=120000`,user,password,namespace,privateFile,publicFile,kid,issuer,bootstrap,serviceKey};
  async function stop(i) {
    const child = children.get(i); if (!child) return;
    if (child.exitCode === null && child.signalCode === null) {
      let dumpFailure;
      try {
        // Windows TerminateProcess does not run Java shutdown hooks. Use the
        // official JaCoCo CLI while this exact owned JVM is still alive.
        if(agent) {
          const dest=resolve(ROOT,`rbac-${module}/target/jacoco-acceptance/${name}-${i}-${++dumpSequence}.exec`);
          execFileSync(java,['-jar',cli,'dump','--address','127.0.0.1','--port',String(coveragePorts[i]),'--retry','0','--destfile',dest],{env:hostEnvironment(process.env),timeout:30000,stdio:['ignore','pipe','pipe']});
          if((await readFile(dest)).length===0) throw new Error('Empty coverage dump');
        }
      } catch {dumpFailure=new Error('Owned JVM coverage dump failed; refusing incomplete coverage evidence');}
      finally {
        const done = new Promise(r => child.once('exit',r)); child.kill('SIGTERM');
        await Promise.race([done,sleep(10000)]);
        if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await done; }
      }
      if(dumpFailure) throw dumpFailure;
    }
    children.delete(i);
  }
  async function start(i) {
    await freePort(ports[i]);
    const log = createWriteStream(join(dir,`${module}-${i}.log`), {flags:'a', mode:0o600}); streams.push(log);
    const coverageArgs=[];
    if (agent) {
      // Choose a fresh private agent port for every JVM incarnation. Fixed
      // diagnostic ports can briefly collide with unrelated local test JVMs.
      coveragePorts[i]=await availableLoopbackPort();
      const coverageDir=resolve(ROOT,`rbac-${module}/target/jacoco-acceptance`);
      await mkdir(coverageDir,{recursive:true});
      coverageArgs.push(`-javaagent:${agent}=output=tcpserver,address=127.0.0.1,port=${coveragePorts[i]},dumponexit=false`);
    }
    const env=applicationEnvironment(process.env,{...applicationConfig,port:ports[i]});
    const child = spawn(java,['-Xms128m','-Xmx384m',...coverageArgs,'-jar',runtimeJar,'--spring.config.location=classpath:/'], {env,cwd:ROOT,stdio:['ignore','pipe','pipe']});
    children.set(i,child); child.stdout.pipe(log);child.stderr.pipe(log);
    let spawnError; child.on('error',e => {spawnError=e;});
    const until = Date.now()+180000;
    while(Date.now()<until) {
      if(spawnError || child.exitCode !== null) throw new Error(`Owned JVM ${i} failed; see external log directory ${dir}`);
      try {const r = await fetch(`http://127.0.0.1:${ports[i]}/api/v1/actuator/health`, {redirect:'error',signal:AbortSignal.timeout(2000)}); if(r.ok) return;} catch {}
      await sleep(500);
    }
    throw new Error(`Owned JVM readiness timeout; see ${dir}`);
  }
  async function cleanup() {
    const errors=[];
    for(const i of [...children.keys()]) try {await stop(i);} catch {errors.push('owned process cleanup failed');}
    for(const s of streams) s.end();
    try {if(ownedDatabase) await clearOwnedNamespace(name);} catch {errors.push('owned Redis namespace cleanup failed');}
    try {if(db) await db.end();} catch {errors.push('database connection close failed');}
    try {if(ownedDatabase) {guardDatabase(host,port,name);await admin.query(`DROP DATABASE ${quoted}`);}} catch {errors.push('owned database cleanup failed');}
    try {if(admin) await admin.end();} catch {}
    // Logs retained outside repository; remove ephemeral private/public keys only.
    for(const f of [privateFile,publicFile,runtimeJar]) await rm(f,{force:true});
    if(errors.length) throw new Error(errors.join('; '));
  }
  try {
    // Dedicated Redis must be reachable; only a PING is issued during readiness.
    await new Promise((res,rej) => {const s=net.connect({host:'127.0.0.1',port:16379}); s.setTimeout(10000);s.once('connect',()=>s.write('*1\r\n$4\r\nPING\r\n'));s.once('data',b=>{s.destroy();b.toString().startsWith('+PONG')?res():rej(new Error('Dedicated Redis PING failed'));});s.once('error',()=>rej(new Error('Dedicated Redis unavailable')));s.once('timeout',()=>{s.destroy();rej(new Error('Dedicated Redis timeout'));});});
    admin = await mysql.createConnection({host,port,user,password,timezone:'Z'});
    await admin.query(`CREATE DATABASE ${quoted} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`); ownedDatabase=true;
    await start(0); await start(1);
    db=await mysql.createConnection({host,port,user,password,database:name,timezone:'Z',dateStrings:true});
    return {module,ports,name,dir,jarSha256,db,namespace,bootstrap,serviceKey,keys:{privateKey,publicKey,kid,issuer},cleanup,restart:async i=>{try {await stop(i);await start(i);} catch {const e=new Error('Owned process restart failed');e.name='FixtureEnvironmentError';throw e;}}};
  } catch(e) {
    // Preserve the exact tested snapshot identity even when readiness fails.
    e.fixture={database:name,logDirectory:dir,ports,jarSha256};
    try {await cleanup();} catch {e.cleanupFailed=true;}
    throw e;
  }
}
