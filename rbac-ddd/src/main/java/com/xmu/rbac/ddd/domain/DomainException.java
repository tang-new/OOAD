package com.xmu.rbac.ddd.domain;

/** 可跨接入协议翻译的稳定领域错误。 */
public final class DomainException extends RuntimeException {
    private final int status;
    private final String code;
    private final boolean auditDenial;

    public DomainException(int status, String code) {
        this(status, code, false);
    }

    private DomainException(int status, String code, boolean auditDenial) {
        super(code);
        this.status = status;
        this.code = code;
        this.auditDenial = auditDenial;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean requiresDenialAudit() {
        return auditDenial;
    }

    public static DomainException auditedDenial(int status, String code) {
        return new DomainException(status, code, true);
    }

    public static void require(boolean condition, int status, String code) {
        if (!condition) throw new DomainException(status, code);
    }
}
