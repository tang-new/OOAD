package com.xmu.rbac.ddd.infrastructure.identity;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.CredentialEncoder;

import jakarta.persistence.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 只初始化 UNINITIALIZED，重启绝不重置已有口令。 */
@Component
public class BootstrapInitializer implements ApplicationRunner {
    @PersistenceContext private EntityManager em;
    private final String password;
    private final CredentialEncoder encoder;

    public BootstrapInitializer(
            @Value("${RBAC_BOOTSTRAP_PASSWORD:}") String password, CredentialEncoder encoder) {
        this.password = password;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        em.createNativeQuery("select version from sys_grant_version where id=1 for update")
                .getSingleResult();
        String hash =
                (String)
                        em.createNativeQuery(
                                        "select password_hash from sys_user where id=1 for update")
                                .getSingleResult();
        if ("UNINITIALIZED".equals(hash)) {
            DomainException.require(password.length() >= 12, 503, "BOOTSTRAP_PASSWORD_REQUIRED");
            em.createNativeQuery(
                            "update sys_user set password_hash=:hash where id=1 and"
                                    + " password_hash='UNINITIALIZED'")
                    .setParameter("hash", encoder.encode(password))
                    .executeUpdate();
        }
    }
}
