package com.stockmate.stockmate.repository;



import com.stockmate.stockmate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the users table.
 *
 * Queries needed by the service layer:
 *  - CustomUserDetailsService  → findByUsername()          [Spring Security login]
 *  - UserService.register()    → existsByUsername/Email()  [duplicate check]
 *  - AdminService              → findAll()                 [list all users]
 *  - AdminService.changeRole() → findById()                [update role]
 *  - AdminService.disable()    → findById() + save()       [toggle enabled]
 *
 * No business logic here — just query declarations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Used by CustomUserDetailsService.loadUserByUsername().
     * Spring Security calls this on every login attempt.
     * Roles are EAGER so they load in this same query.
     */
    Optional<User> findByUsername(String username);

    /**
     * Used by CustomUserDetailsService and AuthService as a fallback
     * if the system needs to look up a user by email.
     */
    Optional<User> findByEmail(String email);

    /**
     * Duplicate check during registration — fails fast before
     * attempting an INSERT that would hit the UNIQUE constraint.
     */
    boolean existsByUsername(String username);

    /**
     * Duplicate check during registration — same pattern as above.
     */
    boolean existsByEmail(String email);

    /**
     * Admin: list all users who hold a specific role name.
     * Used by AdminService to filter SELLER-only or BUYER-only lists.
     * JOIN FETCH roles avoids an N+1 query when mapping to UserResponse DTOs.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.roles r WHERE r.name = :roleName")
    List<User> findAllByRoleName(String roleName);
}