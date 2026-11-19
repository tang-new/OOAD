package cn.edu.xmu.projectmgt.integration;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;

/** Only trusted backend clients can obtain current authority codes. */
@HttpExchange
public interface CentralClient {
    record Decision(boolean allowed) {}
    record Authorities(List<String> authorities) {}
    @GetExchange("/auth/api-permissions/{operationId}")
    Decision operation(
            @RequestHeader("Authorization")
            String bearer,
            @PathVariable("operationId")
            String operationId
    );
    @GetExchange("/integration/authorities")
    Authorities authorities(
            @RequestHeader("Authorization")
            String bearer
    );
}
