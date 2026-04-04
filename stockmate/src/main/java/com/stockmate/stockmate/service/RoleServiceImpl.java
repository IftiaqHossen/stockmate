package com.stockmate.stockmate.service;

import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RoleServiceImpl — single-responsibility: look up seeded roles safely.
 *
 * Constructor injection is used (never @Autowired field injection).
 * The dependency is final — immutable after construction.
 *
 * No @PreAuthorize needed here — callers (UserService, AdminService) handle
 * their own access control.  This class is a pure infrastructure helper.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    // ── Constructor injection ─────────────────────────────────

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    // ── RoleService ───────────────────────────────────────────

    /**
     * findByName
     *
     * Inputs     : roleName — e.g. "ROLE_BUYER", "ROLE_SELLER", "ROLE_ADMIN"
     * Validation : Optional.empty() → ResourceNotFoundException
     *              (missing seed row is a programmer / ops error, not a user error)
     * Repository : RoleRepository.findByName()
     * Business   : read-only, no side-effects
     * Output     : Role entity (caller adds it to User.roles)
     */
    @Override
    public Role findByName(String roleName) {
        log.debug("Looking up role: {}", roleName);
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> {
                    log.error("Seed role missing from DB: {}", roleName);
                    return new ResourceNotFoundException("Role not found: " + roleName);
                });
    }
}