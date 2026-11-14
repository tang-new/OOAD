package com.xmu.rbac.structured.service;
/** 稳定字符串业务错误，禁止向外输出异常详情。 */
public class BizException extends RuntimeException {
    public final int status;
    public final String code;
    public BizException(int status, String code) {
        super("请求未完成");
        this.status = status;
        this.code = code;
    }
    /** 执行bad操作，保持服务到持久化层的单向依赖。 */
    public static BizException bad(String code) {
        return new BizException(400, code);
    }
    /** 执行forbidden操作，保持服务到持久化层的单向依赖。 */
    public static BizException forbidden(String code) {
        return new BizException(403, code);
    }
    /** 执行conflict操作，保持服务到持久化层的单向依赖。 */
    public static BizException conflict(String code) {
        return new BizException(409, code);
    }
    /** 执行missing操作，保持服务到持久化层的单向依赖。 */
    public static BizException missing(String code) {
        return new BizException(404, code);
    }
    /** 执行unauthorized操作，保持服务到持久化层的单向依赖。 */
    public static BizException unauthorized() {
        return new BizException(401, "UNAUTHENTICATED");
    }
}
