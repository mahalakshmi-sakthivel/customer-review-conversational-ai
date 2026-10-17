package com.reviewai.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Creates the first SUPER_ADMIN so login can be exercised. Does nothing unless both
 * BOOTSTRAP_SUPER_ADMIN_EMAIL and BOOTSTRAP_SUPER_ADMIN_PASSWORD are set, and never when a
 * SUPER_ADMIN already exists. The password is never logged. Remove the variables after first start.
 */
@Component
public class BootstrapSuperAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapSuperAdminRunner.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public BootstrapSuperAdminRunner(
        AppUserRepository users,
        PasswordEncoder passwordEncoder,
        @Value("${app.bootstrap.super-admin.email:}") String email,
        @Value("${app.bootstrap.super-admin.password:}") String password
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            return;
        }
        if (users.existsByRole(Role.SUPER_ADMIN)) {
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("Bootstrap super admin skipped: password must be at least {} characters", MIN_PASSWORD_LENGTH);
            return;
        }
        users.save(new AppUser(UUID.randomUUID(), null, email.trim(),
            passwordEncoder.encode(password), Role.SUPER_ADMIN, UserStatus.ACTIVE));
        log.info("Bootstrap SUPER_ADMIN created. Remove BOOTSTRAP_SUPER_ADMIN_* from the environment.");
    }
}
