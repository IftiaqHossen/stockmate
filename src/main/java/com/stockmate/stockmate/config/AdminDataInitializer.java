package com.stockmate.stockmate.config;

import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.repository.RoleRepository;
import com.stockmate.stockmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures a default admin account exists in every environment.
 * This protects fresh cloud databases where SQL seed files are not executed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements ApplicationRunner {

    private static final String ADMIN_ROLE_NAME = "ROLE_ADMIN";
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@stockmate.com";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role adminRole = roleRepository.findByName(ADMIN_ROLE_NAME)
                .orElseGet(() -> {
                    Role created = roleRepository.save(new Role(ADMIN_ROLE_NAME));
                    log.info("Seeded missing role for admin initializer: {}", ADMIN_ROLE_NAME);
                    return created;
                });

        User adminUser = userRepository.findByUsername(DEFAULT_ADMIN_USERNAME)
                .orElseGet(() -> createDefaultAdmin(adminRole));

        boolean updated = false;

        if (!adminUser.isEnabled()) {
            adminUser.setEnabled(true);
            updated = true;
        }

        boolean hasAdminRole = adminUser.getRoles().stream()
                .anyMatch(role -> ADMIN_ROLE_NAME.equals(role.getName()));

        if (!hasAdminRole) {
            adminUser.addRole(adminRole);
            updated = true;
        }

        if (updated) {
            userRepository.save(adminUser);
            log.info("Updated default admin account baseline settings");
        }
    }

    private User createDefaultAdmin(Role adminRole) {
        User adminUser = new User(
                DEFAULT_ADMIN_USERNAME,
                DEFAULT_ADMIN_EMAIL,
                passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD)
        );
        adminUser.setEnabled(true);
        adminUser.addRole(adminRole);

        User saved = userRepository.save(adminUser);
        log.info("Seeded default admin account: {}", DEFAULT_ADMIN_USERNAME);
        return saved;
    }
}
