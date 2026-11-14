// Child JVMs never inherit application/JVM configuration from the caller.
// Canonical Spring JSON overrides both independent backends' different aliases.
export function hostEnvironment(inherited) {
  return Object.fromEntries(Object.entries(inherited).filter(([key]) => /^(PATH|PATHEXT|SYSTEMROOT|WINDIR|COMSPEC|JAVA_HOME|HOME|USERPROFILE|TEMP|TMP|LANG|LC_.+|TZ)$/i.test(key)));
}
export function applicationEnvironment(inherited, config) {
  const env=hostEnvironment(inherited);
  Object.assign(env,{
    JAVA_HOME:config.javaHome,
    SPRING_PROFILES_ACTIVE:'test',
    SERVER_ADDRESS:'127.0.0.1', SERVER_PORT:String(config.port),
    RBAC_BOOTSTRAP_PASSWORD:config.bootstrap,
    RBAC_AUDIT_SERVICE_KEY:config.serviceKey,
    SPRING_APPLICATION_JSON:JSON.stringify({
      server:{address:'127.0.0.1',port:config.port},
      spring:{
        datasource:{url:config.url,username:config.user,password:config.password},
        flyway:{url:config.url,user:config.user,password:config.password,enabled:true,'clean-disabled':true,locations:['classpath:db/migration']},
        jpa:{'generate-ddl':false,hibernate:{'ddl-auto':'validate'}},
        data:{redis:{host:'127.0.0.1',port:16379,username:'',password:''}}
      },
      rbac:{
        'bootstrap-password':config.bootstrap,'audit-service-key':config.serviceKey,
        cache:{namespace:config.namespace},
        jwt:{'private-key-file':config.privateFile,'public-key-file':config.publicFile,kid:config.kid,issuer:config.issuer,'allow-ephemeral':false,'previous-public-keys':''}
      }
    })
  });
  return env;
}
