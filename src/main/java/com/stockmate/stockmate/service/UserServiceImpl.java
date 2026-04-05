package com.stockmate.stockmate.service;


import com.stockmate.stockmate.dto.request.RegisterRequest;
import com.stockmate.stockmate.dto.response.UserResponse;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UserServiceImpl — all user lifecycle rules.
 *
 * Dependencies (constructor-injected, all final):
 *   UserRepository   — DB access for users table
 *   RoleService      — safe role lookup (no direct RoleRepository dependency here)
 *   PasswordEncoder  — BCrypt provided by SecurityConfig bean
 *
 * No circular dependency:
 *   UserServiceImpl → RoleService → RoleRepository   ✅
 *   No back-reference to controllers or security classes ✅
 */
@Slf4j
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final RoleService     roleService;
    private final PasswordEncoder passwordEncoder;

    // ── Constructor injection ─────────────────────────────────

    public UserServiceImpl(UserRepository userRepository,
                           RoleService roleService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.roleService     = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Registration ──────────────────────────────────────────

    /**
     * register
     *
     * Inputs     : RegisterRequest { username, email, password, role }
     * Validation : username not taken · email not taken
     *              role must be "ROLE_BUYER" or "ROLE_SELLER" (ADMIN not self-assignable)
     * Repos      : UserRepository (existsBy* + save) · RoleService.findByName()
     * Business   : BCrypt-hash password · assign chosen role · persist
     * Output     : UserResponse (no password)
     */
    @Override
    public UserResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.username());

        // ── Duplicate checks ──────────────────────────────────
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException(
                    "Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "Email already registered: " + request.email());
        }

        // ── Role guard — ADMIN cannot be self-assigned ────────
        String requestedRole = request.role();   // expects "ROLE_BUYER" or "ROLE_SELLER"
        if ("ROLE_ADMIN".equalsIgnoreCase(requestedRole)) {
            throw new IllegalArgumentException(
                    "ADMIN role cannot be self-assigned at registration.");
        }

        // ── Build and persist user ────────────────────────────
        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password())
        );

        Role role = roleService.findByName(requestedRole);
        user.addRole(role);

        User saved = userRepository.save(user);
        log.info("User registered with id={}, role={}", saved.getId(), requestedRole);

        return toResponse(saved);
    }

    // ── Admin: list users ─────────────────────────────────────

    /**
     * getAllUsers
     *
     * Inputs     : none
     * Validation : ADMIN only (method-level security)
     * Repos      : UserRepository.findAll()
     * Business   : none — map to DTO
     * Output     : List<UserResponse>
     */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getAllUsers() {
        log.debug("Admin: fetching all users");
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Admin: change role ────────────────────────────────────

    /**
     * changeRole
     *
     * Inputs     : userId, newRoleName
     * Validation : user must exist · role must exist · ADMIN only
     * Repos      : UserRepository · RoleService
     * Business   : clear all current roles, assign single new role
     * Output     : updated UserResponse
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse changeRole(Long userId, String newRoleName) {
        log.info("Admin: changing role of userId={} to {}", userId, newRoleName);

        User user = findUserOrThrow(userId);
        Role newRole = roleService.findByName(newRoleName);

        user.setRoles(Set.of(newRole));
        User saved = userRepository.save(user);

        log.info("Role updated: userId={} → {}", saved.getId(), newRoleName);
        return toResponse(saved);
    }

    // ── Admin: disable user ───────────────────────────────────

    /**
     * disableUser
     *
     * Inputs     : userId
     * Validation : user must exist · ADMIN only
     * Repos      : UserRepository
     * Business   : soft-disable (enabled = false); preserves FK references
     * Output     : void
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void disableUser(Long userId) {
        log.info("Admin: disabling userId={}", userId);
        User user = findUserOrThrow(userId);
        user.setEnabled(false);
        userRepository.save(user);
        log.info("User disabled: userId={}", userId);
    }

    // ── Admin: delete user ────────────────────────────────────

    /**
     * deleteUser
     *
     * Inputs     : userId
     * Validation : user must exist · ADMIN only
     * Repos      : UserRepository
     * Business   : hard delete — caller must verify no FK violations
     * Output     : void
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long userId) {
        log.info("Admin: deleting userId={}", userId);
        User user = findUserOrThrow(userId);
        userRepository.delete(user);
        log.info("User deleted: userId={}", userId);
    }

    // ── Internal helper (for security layer) ─────────────────

    /**
     * findByUsername
     *
     * Inputs     : username
     * Validation : throws ResourceNotFoundException if not found
     * Repos      : UserRepository.findByUsername()
     * Business   : none
     * Output     : UserResponse
     */
    @Override
    @Transactional(readOnly = true)
    public UserResponse findByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));
        return toResponse(user);
    }

    // ── Private helpers ───────────────────────────────────────

    /** Shared "find or throw" to keep method bodies DRY. */
    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));
    }

    /**
     * Entity → DTO mapping.
     * Extracts role names from the Set<Role> without exposing the entity.
     * Password is deliberately excluded from the response.
     */
    private UserResponse toResponse(User user) {
        Set<String> roleNames = user.getRoles()
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled(),
                user.getCreatedAt(),
                roleNames
        );
    }
}