package com.xmu.rbac.ddd.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.function.Supplier;

/** 只读外层事务的权限快照使用独立 READ COMMITTED，避免旧的一致性读快照被重试复用。 */
@Service
public class AuthorizationReadTransaction {
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            readOnly = true)
    public <T> T fresh(Supplier<T> query) {
        return query.get();
    }
}
