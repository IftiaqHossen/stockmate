package com.stockmate.stockmate.controller;

import com.stockmate.stockmate.dto.request.CreateCategoryRequest;
import com.stockmate.stockmate.dto.response.CategoryResponse;
import com.stockmate.stockmate.service.CategoryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * CategoryController — product category management.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * RESPONSIBILITY BOUNDARY (CLAUDE.md)
 * ══════════════════════════════════════════════════════════════════════════
 *   ✅ Maps URLs → CategoryService calls
 *   ✅ Passes validated DTOs to the service
 *   ✅ Manages flash messages and redirects
 *   ❌ No business logic — duplicate-name check, delete guard → service layer
 *   ❌ Never touches a repository directly
 *   ❌ Never returns a JPA entity
 *
 * ACCESS CONTROL (two-layer)
 * ──────────────────────────
 *   Layer 1 (SecurityConfig):
 *     POST/PUT/DELETE /categories/** → ADMIN only
 *     GET /categories/**            → any authenticated user
 *   Layer 2 (@PreAuthorize in CategoryServiceImpl):
 *     createCategory / updateCategory / deleteCategory → hasRole('ADMIN')
 *
 * ENDPOINT SUMMARY  (FR-CAT-01 / FR-CAT-02)
 * ──────────────────────────────────────────
 *   GET    /categories          → list all categories (all authenticated)
 *   GET    /categories/new      → show create form (ADMIN)
 *   POST   /categories          → create category (ADMIN)
 *   GET    /categories/{id}/edit → show edit form (ADMIN)
 *   PUT    /categories/{id}     → update category (ADMIN)
 *   DELETE /categories/{id}     → delete category (ADMIN)
 *
 * HTML FORM NOTE:
 *   PUT and DELETE use Spring's HiddenHttpMethodFilter.
 *   Requires: spring.mvc.hiddenmethod.filter.enabled=true
 */
@Slf4j
@Controller
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    // ── Constructor injection ─────────────────────────────────

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // ══════════════════════════════════════════════════════════
    //  GET /categories — list all categories
    //  FR-CAT-02: used by product forms AND admin management page
    // ══════════════════════════════════════════════════════════

    /**
     * Lists all categories.
     * Accessible by any authenticated user — SELLERs need this for product forms,
     * ADMINs need it for the management page, BUYERs need it for filtering.
     */
    @GetMapping
    public String listCategories(Model model) {
        List<CategoryResponse> categories = categoryService.getAllCategories();
        model.addAttribute("categories",       categories);
        // Empty DTO so Thymeleaf can bind the inline "quick add" form on the page
        model.addAttribute("categoryRequest",
                new CreateCategoryRequest("", ""));
        return "admin/categories";   // → templates/admin/categories.html
    }

    // ══════════════════════════════════════════════════════════
    //  GET /categories/new — show create form (ADMIN)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("categoryRequest", new CreateCategoryRequest("", ""));
        model.addAttribute("formAction", "create");
        return "admin/category-form";   // → templates/admin/category-form.html
    }

    // ══════════════════════════════════════════════════════════
    //  POST /categories — create a category (ADMIN)
    //  FR-CAT-01
    // ══════════════════════════════════════════════════════════

    /**
     * Processes category creation.
     *
     * @Valid catches empty names or length violations.
     * IllegalArgumentException (duplicate name) is caught here for
     * better UX — redisplays the form with an inline error instead of
     * routing to GlobalExceptionHandler's 400 page.
     */
    @PostMapping
    public String createCategory(
            @Valid @ModelAttribute("categoryRequest") CreateCategoryRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("formAction", "create");
            return "admin/category-form";
        }

        try {
            CategoryResponse created = categoryService.createCategory(request);
            log.info("Category created: id={}, name={}", created.id(), created.name());
            redirectAttrs.addFlashAttribute("successMessage",
                    "Category '" + created.name() + "' created successfully.");
            return "redirect:/categories";

        } catch (IllegalArgumentException ex) {
            log.warn("Category creation rejected: {}", ex.getMessage());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("formAction",   "create");
            return "admin/category-form";
        }
    }

    // ══════════════════════════════════════════════════════════
    //  GET /categories/{id}/edit — show edit form (ADMIN)
    // ══════════════════════════════════════════════════════════

    /**
     * Shows the edit form pre-populated with the category's current values.
     * ResourceNotFoundException (unknown id) → GlobalExceptionHandler → 404.
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        CategoryResponse category = categoryService.getCategoryById(id);
        model.addAttribute("categoryRequest",
                new CreateCategoryRequest(category.name(), category.description()));
        model.addAttribute("category",   category);
        model.addAttribute("formAction", "edit");
        return "admin/category-form";
    }

    // ══════════════════════════════════════════════════════════
    //  PUT /categories/{id} — update category (ADMIN)
    //  FR-CAT-01
    // ══════════════════════════════════════════════════════════

    /**
     * Processes the edit form.
     * Name-conflict with another category (IllegalArgumentException) is
     * caught here for inline form error UX.
     */
    @PutMapping("/{id}")
    public String updateCategory(
            @PathVariable Long id,
            @Valid @ModelAttribute("categoryRequest") CreateCategoryRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("category",   categoryService.getCategoryById(id));
            model.addAttribute("formAction", "edit");
            return "admin/category-form";
        }

        try {
            CategoryResponse updated = categoryService.updateCategory(id, request);
            log.info("Category updated: id={}, name={}", updated.id(), updated.name());
            redirectAttrs.addFlashAttribute("successMessage",
                    "Category '" + updated.name() + "' updated.");
            return "redirect:/categories";

        } catch (IllegalArgumentException ex) {
            log.warn("Category update rejected: {}", ex.getMessage());
            model.addAttribute("category",     categoryService.getCategoryById(id));
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("formAction",   "edit");
            return "admin/category-form";
        }
    }

    // ══════════════════════════════════════════════════════════
    //  DELETE /categories/{id} — delete category (ADMIN)
    //  FR-CAT-01
    //
    //  HTML workaround: <input type="hidden" name="_method" value="delete"/>
    // ══════════════════════════════════════════════════════════

    /**
     * Deletes a category.
     *
     * IllegalStateException (category still has products) is caught here
     * so the ADMIN sees the list page with an inline error instead of
     * an ugly error view.
     *
     * ResourceNotFoundException (unknown id) propagates to GlobalExceptionHandler → 404.
     */
    @DeleteMapping("/{id}")
    public String deleteCategory(@PathVariable Long id,
                                 RedirectAttributes redirectAttrs) {
        try {
            categoryService.deleteCategory(id);
            log.info("Category deleted: id={}", id);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Category deleted successfully.");

        } catch (IllegalStateException ex) {
            log.warn("Category delete blocked: {}", ex.getMessage());
            redirectAttrs.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/categories";
    }
}