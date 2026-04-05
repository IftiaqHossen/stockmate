package com.stockmate.stockmate.service;

import com.stockmate.stockmate.model.Role;

/**
 * RoleService — thin lookup façade over the roles table.
 *
 * Roles are seeded by SQL (ROLE_ADMIN, ROLE_SELLER, ROLE_BUYER) and are
 * never created or deleted at runtime.  This service exists so that
 * UserService and AdminService never reach directly into RoleRepository,
 * keeping the dependency graph clean:
 *
 *   UserServiceImpl → RoleService → RoleRepository   ✅
 *   UserServiceImpl → RoleRepository                  ❌  (extra coupling)
 */
public interface RoleService {

    /**
     * Fetch a role by its exact name (e.g. "ROLE_BUYER").
     *
     * Inputs     : roleName — non-null string matching a seeded role
     * Validation : throws ResourceNotFoundException if the name is not in DB
     *              (indicates missing seed data — fail fast, do not return null)
     * Repository : RoleRepository.findByName()
     * Business   : read-only lookup — no mutation
     * Output     : the Role entity (used internally; never returned to controllers)
     */
    Role findByName(String roleName);
}