package com.psy.trainer.config;

import com.psy.trainer.domain.AppRole;
import com.psy.trainer.domain.AppUser;
import com.psy.trainer.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/**
 * Seeds the default admin account when the user table is empty.
 */
@Component
public class SeedUsersRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedUsersRunner.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PsyProperties properties;

    public SeedUsersRunner(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            PsyProperties properties
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        PsyProperties.AuthSeed seed = properties.getAuthSeed();
        users.save(AppUser.create(
                seed.getAdminUsername(),
                passwordEncoder.encode(seed.getAdminPassword()),
                "Admin",
                null,
                EnumSet.of(AppRole.ADMIN, AppRole.THERAPIST)
        ));
        log.info("Seeded default admin user: {}", seed.getAdminUsername());
    }
}
