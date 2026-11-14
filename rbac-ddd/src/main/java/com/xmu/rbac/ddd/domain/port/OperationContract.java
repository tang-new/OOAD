package com.xmu.rbac.ddd.domain.port;

import com.xmu.rbac.ddd.domain.ApiOperation;

import java.util.*;

/** 唯一 OAS 的 API 元数据端口，不与其它实现共享授权算法。 */
public interface OperationContract {
    List<ApiOperation> operations();
}
