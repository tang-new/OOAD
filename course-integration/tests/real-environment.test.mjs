import test from 'node:test';
import assert from 'node:assert/strict';
import {hostEnvironment,runtimeEnvironment,guardDatabase} from './real-environment.mjs';
test('child environment rejects ambient JVM/Spring/DB/Redis/proxy/key injection',()=>{
 const inherited={PATH:'os-path',SYSTEMROOT:'os-root',JAVA_TOOL_OPTIONS:'-Dspring.datasource.url=production',JDK_JAVA_OPTIONS:'-agentlib:evil',_JAVA_OPTIONS:'bad',SPRING_APPLICATION_JSON:'{"spring":{"datasource":{"url":"production"}}}',SPRING_CONFIG_LOCATION:'file:/evil',SPRING_PROFILES_ACTIVE:'prod',DB_PASSWORD:'ambient',REDIS_PASSWORD:'ambient',RBAC_JWT_PRIVATE_KEY_FILE:'evil',HTTP_PROXY:'evil'};
 assert.deepEqual(hostEnvironment(inherited),{PATH:'os-path',SYSTEMROOT:'os-root'});
 const env=runtimeEnvironment(inherited,{kind:'center',strategy:'ddd',port:12345,javaHome:'jdk21',url:'jdbc:owned',dbUser:'owned',dbPassword:'owned-test',namespace:'owned-prefix',privateFile:'owned-private',publicFile:'owned-public',bootstrap:'generated-bootstrap',serviceKey:'generated-service',kid:'owned-kid',issuer:'owned-issuer'});
 assert.equal(env.JAVA_TOOL_OPTIONS,undefined);assert.equal(env.SPRING_CONFIG_LOCATION,undefined);
 const c=JSON.parse(env.SPRING_APPLICATION_JSON);assert.equal(c.spring.datasource.url,'jdbc:owned');assert.equal(c.spring.flyway.url,'jdbc:owned');assert.equal(c.spring.flyway['clean-disabled'],true);assert.equal(c.spring.data.redis.host,'127.0.0.1');assert.equal(c.rbac.jwt['private-key-file'],'owned-private');assert.equal(c.rbac['audit-service-key'],'generated-service');assert.equal(c.spring.config.import,'');
});
test('database allowlist binds generated names to designated loopback ports',()=>{
 const suffix='structured_1794670195412_12c8ee40';assert.equal(guardDatabase('center',13306,'m6_course_real_center_'+suffix),'`m6_course_real_center_'+suffix+'`');assert.doesNotThrow(()=>guardDatabase('course',13316,'m6_course_real_course_'+suffix));
 for(const [kind,port,name]of [['center',3306,'production'],['course',13306,'m6_course_real_course_'+suffix],['center',13306,'rbac1_ddd_test'],['course',13316,'course_integration'],['center',13306,'m6_course_real_center_'+suffix+';DROP DATABASE x']])assert.throws(()=>guardDatabase(kind,port,name));
});
