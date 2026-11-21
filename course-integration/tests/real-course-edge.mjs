import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFile, writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {getCACertificates, setDefaultCACertificates} from 'node:tls';

const docker = args => execFileSync('docker', args, {stdio:['ignore','pipe','pipe'], timeout:120000});

/** 使用生产 Nginx 配置与临时 TLS 证书验证真实代理分流。 */
export async function startCourseEdge({name, directory, template, centerPort, coursePort, port, files}) {
  assert.match(name, /^m6-course-edge-(structured|ddd)-[0-9]+-[a-f0-9]{8}$/);
  const config = join(directory,name+'.conf');
  const cert = join(directory,name+'.crt');
  const key = join(directory,name+'.key');
  files.push(config,cert,key);
  execFileSync(process.env.REAL_OPENSSL || 'openssl', [
    'req','-x509','-newkey','rsa:2048','-days','1','-nodes','-subj','/CN=127.0.0.1',
    '-addext','subjectAltName=IP:127.0.0.1','-keyout',key,'-out',cert,
  ], {stdio:'ignore', timeout:30000});
  setDefaultCACertificates([...getCACertificates('default'),await readFile(cert,'utf8')]);

  let source = await readFile(template,'utf8');
  const hostNetwork = process.platform === 'linux';
  if (hostNetwork) {
    // Linux 的 Docker host 网络可访问仅绑定回环地址的测试服务。
    source = source.replace('listen 443 ssl;', 'listen 127.0.0.1:'+port+' ssl;');
  }
  await writeFile(config,source,{mode:0o600});
  const host = hostNetwork ? '127.0.0.1' : 'host-gateway';
  docker(['run','-d','--rm','--name',name,
    ...(hostNetwork ? ['--network','host'] : ['-p','127.0.0.1:'+port+':443']),
    '--add-host','rbac-backend:'+host,'--add-host','course-backend:'+host,
    '-e','RBAC_PORT='+centerPort,'-e','COURSE_PORT='+coursePort,
    '-v',config+':/etc/nginx/templates/default.conf.template:ro',
    '-v',cert+':/run/secrets/tls.crt:ro','-v',key+':/run/secrets/tls.key:ro',
    'nginx:1.27-alpine']);
  const origin = 'https://127.0.0.1:'+port;
  const until = Date.now()+30000;
  while (Date.now()<until) {
    try {
      const response = await fetch(origin+'/.well-known/jwks.json', {
        redirect:'error',signal:AbortSignal.timeout(2000),
      });
      await response.body?.cancel();
      if (response.ok) return origin;
    } catch {
      // 容器入口脚本生成代理配置后才监听。
    }
    await new Promise(resolve=>setTimeout(resolve,500));
  }
  throw new Error('Course Nginx readiness check failed');
}

/** 清理本轮唯一命名的临时代理容器。 */
export function stopCourseEdge(name) {
  assert.match(name, /^m6-course-edge-(structured|ddd)-[0-9]+-[a-f0-9]{8}$/);
  if (!docker(['ps','-a','--filter','name=^/'+name+'$','--format','{{.Names}}']).toString().trim()) return;
  docker(['stop',name]);
}
