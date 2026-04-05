package com.stockmate.stockmate.service;

import com.stockmate.stockmate.dto.request.CreateCategoryRequest;
import com.stockmate.stockmate.dto.response.CategoryResponse;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Category;
import com.stockmate.stockmate.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CategoryServiceImpl — all category CRUD rules.
 *
 * Dependencies (constructor-injected, final):
 *   CategoryRepository — single dependency (no coupling to other services)
 *
 * Coupling check:
 *   ✅ No reference to ProductService (no circular dep)
 *   ✅ No reference to UserService
 *   ✅ No business logic leaking into controller
 */
@Slf4j
@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    // ── Constructor injection ─────────────────────────────────

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // ── Read ──────────────────────────────────────────────────

    /**
     * getAllCategories
     *
     * Inputs     : none
     * Validation : any authenticated user
     * Repos      : CategoryRepository.findAll()
     * Business   : none — map to DTO
     * Output     : List<CategoryResponse>
     */
    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        log.debug("Fetching all categories");
        return categoryRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * getCategoryById
     *
     * Inputs     : id
     * Validation : throws ResourceNotFoundException if absent
     * Repos      : CategoryRepository.findById()
     * Business   : none
     * Output     : CategoryResponse
     */
    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        log.debug("Fetching category id={}", id);
        Category category = findOrThrow(id);
        return toResponse(category);
    }

    // ── Write (ADMIN only) ────────────────────────────────────

    /**
     * createCategory
     *
     * Inputs     : CreateCategoryRequest { name, description }
     * Validation : name uniqueness guard before INSERT
     * Repos      : CategoryRepository.existsByName() + save()
     * Business   : duplicate name → IllegalArgumentException (400)
     * Output     : CategoryResponse
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Admin: creating category '{}'", request.name());

        if (categoryRepository.existsByName(request.name())) {
            throw new IllegalArgumentException(
                    "Category name already exists: " + request.name());
        }

        Category category = new Category(request.name(), request.description());
        Category saved = categoryRepository.save(category);

        log.info("Category created: id={}, name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    /**
     * updateCategory
     *
     * Inputs     : id, CreateCategoryRequest { name, description }
     * Validation : category must exist · new name must not clash with OTHER categories
     * Repos      : CategoryRepository (findById + findByName + save)
     * Business   : a category can keep its own name (update is idempotent on name)
     * Output     : updated CategoryResponse
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse updateCategory(Long id, CreateCategoryRequest request) {
        log.info("Admin: updating category id={}", id);

        Category category = findOrThrow(id);

        // Name-conflict check: only fail if ANOTHER category owns this name
        categoryRepository.findByName(request.name())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException(
                                "Another category already uses name: " + request.name());
                    }
                });

        category.setName(request.name());
        category.setDescription(request.description());
        Category saved = categoryRepository.save(category);

        log.info("Category updated: id={}, name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    /**
     * deleteCategory
     *
     * Inputs     : id
     * Validation : category must exist · must have zero linked products
     * Repos      : CategoryRepository.hasProducts() + delete()
     * Business   : block delete if products reference this category (FK safety)
     * Output     : void
     */
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCategory(Long id) {
        log.info("Admin: deleting category id={}", id);

        Category category = findOrThrow(id);

        if (categoryRepository.hasProducts(id)) {
            throw new IllegalStateException(
                    "Cannot delete category id=" + id +
                            " — it still has products assigned to it. " +
                            "Re-assign or delete those products first.");
        }

        categoryRepository.delete(category);
        log.info("Category deleted: id={}", id);
    }

    // ── Private helpers ───────────────────────────────────────

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + id));
    }

    /** Entity → DTO — keeps the entity inside the service layer. */
    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.getDescription()
        );
    }
}