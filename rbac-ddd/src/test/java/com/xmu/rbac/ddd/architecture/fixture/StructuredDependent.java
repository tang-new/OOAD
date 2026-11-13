package com.xmu.rbac.ddd.architecture.fixture;

import com.xmu.rbac.structured.archfixture.StructuredMarker;

/** 仅供架构反例测试使用的违规依赖，不参与组件扫描或生产打包。 */
public final class StructuredDependent {
    private StructuredMarker forbidden;
}
