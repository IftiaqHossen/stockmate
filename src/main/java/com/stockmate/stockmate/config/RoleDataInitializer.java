package com.stockmate.stockmate.config;

import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ensures required security roles exist in every environment.
 * This keeps production startup resilient when the roles table is empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleDataInitializer implements ApplicationRunner {

    private static final List<String> REQUIRED_ROLES = List.of(
            "ROLE_ADMIN",
            "ROLE_SELLER",
            "ROLE_BUYER"
    );

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String roleName : REQUIRED_ROLES) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                roleRepository.save(new Role(roleName));
                log.info("Seeded missing role: {}", roleName);
            }
        }
    }
}
