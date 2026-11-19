package com.xmu.tools.clonefactory;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyNotNullFrom;
import cn.edu.xmu.clonefactory.CopyTo;

/** 覆盖 0.0.2 的不可变目标和直接字段访问。 */
public final class ImmutableMappings {
    private ImmutableMappings() {}

    /** 输入是公开展示资料，身份字段由调用方提供。 */
    @CopyTo(PublicFields.class)
    public record Profile(String name, Integer age, Boolean enabled) {}

    /** 公共字段用于验证无需 getter/setter 的兼容路径。 */
    public static class PublicFields {
        public String name;
        public Integer age;
        public Boolean enabled;
    }

    /** 模板保留身份，普通复制和保留空值的复制共用同一规范构造器。 */
    @CopyFrom(Profile.class)
    @CopyNotNullFrom(Profile.class)
    public record Snapshot(Long id, String name, Integer age, Boolean enabled) {}

    /** 含最终字段的公开类型由匹配参数名称的构造器重新实例化。 */
    @CopyFrom(Profile.class)
    public static final class FinalSnapshot {
        public final String name;
        public final Integer age;

        /** 保留用于编译时映射的同名构造参数。 */
        public FinalSnapshot(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
    }
}
