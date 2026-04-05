package com.stockmate.stockmate.service;

import com.stockmate.stockmate.dto.request.CreateCategoryRequest;
import com.stockmate.stockmate.dto.response.CategoryResponse;

import java.util.List;

/**
 * CategoryService — Admin-managed product categories.
 *
 * Read operations (getAll, getById) are available to all authenticated users
 * so that product forms can populate the category dropdown.
 * Write operations (create, update, delete) are ADMIN-only.
 */
public interface CategoryService {

    // ── Read (all authenticated) ──────────────────────────────

    /**
     * Return all categories — used by product form dropdowns.
     *
     * Inputs     : none
     * Validation : none (any authenticated user)
     * Repos      : CategoryRepository.findAll()
     * Business   : none
     * Output     : List<CategoryResponse>
     */
    List<CategoryResponse> getAllCategories();

    /**
     * Return a single category by ID.
     *
     * Inputs     : id (Long)
     * Validation : throws ResourceNotFoundException if not found
     * Repos      : CategoryRepository.findById()
     * Business   : none
     * Output     : CategoryResponse
     */
    CategoryResponse getCategoryById(Long id);

    // ── Write (ADMIN only) ────────────────────────────────────

    /**
     * Create a new product category — ADMIN only (FR-CAT-01).
     *
     * Inputs     : CreateCategoryRequest { name, description }
     * Validation : name must be unique · @PreAuthorize ADMIN
     * Repos      : CategoryRepository (existsByName + save)
     * Business   : fail fast on duplicate name before hitting DB constraint
     * Output     : CategoryResponse
     */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /**
     * Update an existing category's name and/or description — ADMIN only.
     *
     * Inputs     : id (Long), CreateCategoryRequest { name, description }
     * Validation : category must exist · new name must not conflict with
     *              another existing category · @PreAuthorize ADMIN
     * Repos      : CategoryRepository (findById + existsByName + save)
     * Business   : partial update — only changed fields need be provided
     * Output     : updated CategoryResponse
     */
    CategoryResponse updateCategory(Long id, CreateCategoryRequest request);

    /**
     * Delete a category — ADMIN only.
     *
     * Inputs     : id (Long)
     * Validation : category must exist · must have no associated products
     *              (CategoryRepository.hasProducts()) · @PreAuthorize ADMIN
     * Repos      : CategoryRepository
     * Business   : refuse delete if products reference this category
     *              (throw IllegalStateException, caught by GlobalExceptionHandler)
     * Output     : void
     */
    void deleteCategory(Long id);
}