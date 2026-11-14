package com.xmu.rbac.ddd.domain;

/** 可跨接入协议翻译的稳定领域错误。 */
public final class DomainException extends RuntimeException {
    private final int status;
    private final String code;

    public DomainException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static void require(boolean condition, int status, String code) {
        if (!condition) throw new DomainException(status, code);
    }
}
