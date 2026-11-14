package com.xmu.rbac.ddd.domain.port;

import com.xmu.rbac.ddd.domain.AuthorizationPolicy;

/** 仅缓存带权威版本的不可变授权投影。 */
public interface EffectiveCache {
    AuthorizationPolicy.Effective get(String system, long user, long version);

    void put(String system, long user, long version, AuthorizationPolicy.Effective effective);
}
