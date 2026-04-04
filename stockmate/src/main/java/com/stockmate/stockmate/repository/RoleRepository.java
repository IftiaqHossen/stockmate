package com.stockmate.stockmate.repository;

import com.stockmate.stockmate.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for the roles lookup table.
 *
 * Queries needed by the service layer:
 *  - UserService.register()      → findByName("ROLE_BUYER") or "ROLE_SELLER"
 *  - AdminService.changeRole()   → findByName("ROLE_SELLER") / "ROLE_ADMIN"
 *
 * No custom JPQL needed — Spring Data method names cover everything.
 * Roles are seeded via SQL; this repository is read-only in practice.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Fetch a role by its exact name (e.g. "ROLE_BUYER").
     * Returns Optional so the caller can throw ResourceNotFoundException
     * if the seed data is missing — fail fast rather than NullPointerException.
     */
    Optional<Role> findByName(String name);
}