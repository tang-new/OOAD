package com.xmu.rbac.gateway;

/** 只携带稳定错误码，禁止传递底层异常消息。 */
public final class GatewayFailure extends RuntimeException {
    private final int status;
    private final String code;

    public GatewayFailure(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
