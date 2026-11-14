import test from 'node:test';
import assert from 'node:assert/strict';
import {applicationEnvironment,hostEnvironment} from './environment.mjs';
const config={javaHome:'/jdk21',url:'jdbc:mysql://127.0.0.1:13306/rbac1_contract_ddd_123_abcdef',user:'fixture',password:'temporary-only',port:19082,namespace:'owned:',bootstrap:'temporary-bootstrap',serviceKey:'temporary-service-key',privateFile:'/temporary/private.pem',publicFile:'/temporary/public.pem',kid:'fixture-kid',issuer:'urn:fixture'};
test('child environment does not inherit any Spring, datasource, Redis or JVM override',()=>{
  const inherited={PATH:'/bin',SystemRoot:'C:/Windows',JAVA_HOME:'/jdk21',LANG:'en_US.UTF-8',RBAC_DB_URL:'jdbc:mysql://production.invalid/live',DB_URL:'jdbc:mysql://other.invalid/live',RBAC_REDIS_HOST:'remote.invalid',SPRING_DATASOURCE_URL:'jdbc:mysql://forbidden.invalid/live',SPRING_FLYWAY_URL:'jdbc:mysql://forbidden.invalid/live',SPRING_CONFIG_IMPORT:'http://forbidden.invalid/config',SPRING_APPLICATION_JSON:'{"spring":{"datasource":{"url":"forbidden"}}}',JAVA_TOOL_OPTIONS:'-javaagent:forbidden.jar',JDK_JAVA_OPTIONS:'-Dspring.datasource.url=forbidden',_JAVA_OPTIONS:'-Dspring.flyway.url=forbidden',HTTP_PROXY:'http://forbidden.invalid'};
  const env=applicationEnvironment(inherited,config);
  assert.equal(env.PATH,'/bin');assert.equal(env.SystemRoot,'C:/Windows');
  for(const key of ['RBAC_DB_URL','DB_URL','RBAC_REDIS_HOST','SPRING_DATASOURCE_URL','SPRING_FLYWAY_URL','SPRING_CONFIG_IMPORT','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','HTTP_PROXY']) assert.equal(env[key],undefined,key);
  assert.deepEqual(hostEnvironment(inherited),{PATH:'/bin',SystemRoot:'C:/Windows',JAVA_HOME:'/jdk21',LANG:'en_US.UTF-8'});
  const app=JSON.parse(env.SPRING_APPLICATION_JSON);
  assert.deepEqual(app.spring.datasource,{url:config.url,username:config.user,password:config.password});
  assert.equal(app.spring.flyway.url,config.url);assert.equal(app.spring.flyway.user,config.user);assert.equal(app.spring.flyway.password,config.password);assert.equal(app.spring.flyway['clean-disabled'],true);
  assert.deepEqual(app.spring.data.redis,{host:'127.0.0.1',port:16379,username:'',password:''});
  assert.equal(app.rbac.cache.namespace,'owned:');assert.equal(app.rbac.jwt['previous-public-keys'],'');assert.equal(app.rbac.jwt['allow-ephemeral'],false);
  assert.equal(app.server.address,'127.0.0.1');assert.equal(app.server.port,19082);assert.equal(env.RBAC_BOOTSTRAP_PASSWORD,config.bootstrap);
});
test('canonical pinned configuration is identical for both backend aliases',()=>{
  const a=applicationEnvironment({DB_URL:'forbidden',REDIS_HOST:'forbidden'},config);
  const b=applicationEnvironment({RBAC_DB_URL:'forbidden',RBAC_REDIS_HOST:'forbidden'},config);
  assert.deepEqual(a,b);
});
