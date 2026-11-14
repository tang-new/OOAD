// Child JVMs inherit OS runtime basics only, never caller Spring/JVM/database/proxy configuration.
export function hostEnvironment(inherited) {
  return Object.fromEntries(Object.entries(inherited).filter(([k])=>/^(PATH|PATHEXT|SYSTEMROOT|WINDIR|COMSPEC|HOME|USERPROFILE|TEMP|TMP|LANG|LC_.+|TZ)$/i.test(k)));
}
export function runtimeEnvironment(inherited,c) {
  const shared={
    server:{address:'127.0.0.1',port:c.port},
    spring:{
      config:{import:''},
      datasource:{url:c.url||'jdbc:invalid:disabled',username:c.dbUser||'',password:c.dbPassword||'',hikari:{'maximum-pool-size':2}},
      flyway:{enabled:c.kind!=='gateway',url:c.url||'jdbc:invalid:disabled',user:c.dbUser||'',password:c.dbPassword||'','clean-disabled':true,locations:['classpath:db/migration']},
      jpa:{'open-in-view':false,hibernate:{'ddl-auto':c.kind==='gateway'||(c.kind==='center'&&c.strategy==='structured')?'none':'validate'}},
      data:{redis:{host:'127.0.0.1',port:16379,username:'',password:'',timeout:'2s'}}
    },
    logging:{level:{root:'INFO','cn.edu.xmu.projectmgt.api':'INFO','cn.edu.xmu.projectmgt.service':'INFO','cn.edu.xmu.projectmgt.infrastructure':'INFO','org.springframework.web.client':'WARN','org.springframework.security':'WARN'}},
    management:{endpoints:{web:{'base-path':c.kind==='course'?'/actuator':'/api/v1/actuator',exposure:{include:'health'}}}}
  };
  if(c.kind==='center') shared.management.endpoint={health:{probes:{enabled:true}}};
  shared.rbac={'bootstrap-password':c.bootstrap||'','audit-service-key':c.serviceKey||'',cache:{namespace:c.namespace||''},jwt:{'private-key-file':c.privateFile||'','public-key-file':c.publicFile||'',kid:c.kid||'',issuer:c.issuer||'','allow-ephemeral':false,'previous-public-keys':''}};
  if(c.kind==='course') {
    shared.spring.profiles={active:'integration'};
    shared.app={'backend-strategy':c.strategy||'structured',identity:{'base-url':c.center},integration:{issuer:c.issuer,'jwk-set-uri':c.center+'/.well-known/jwks.json','service-key':c.serviceKey}};
  }
  if(c.kind==='gateway') shared.gateway={'identity-base-url':c.center,'course-base-url':c.course,issuer:c.issuer,'jwk-set-uri':c.center+'/.well-known/jwks.json','allow-http':true,'timeout-ms':30000,'max-request-bytes':10485760,'max-response-bytes':10485760};
  return {...hostEnvironment(inherited),JAVA_HOME:c.javaHome,RBAC_BOOTSTRAP_PASSWORD:c.bootstrap||'',SPRING_APPLICATION_JSON:JSON.stringify(shared)};
}
export function guardDatabase(kind,port,name) {
  if(!['center','course'].includes(kind)||port!==(kind==='center'?13306:13316)||!new RegExp(`^m6_course_real_${kind}_(structured|ddd)_[0-9]{13}_[a-f0-9]{8}$`).test(name))throw Error('Unsafe dedicated database refused');
  return '`'+name+'`';
}
