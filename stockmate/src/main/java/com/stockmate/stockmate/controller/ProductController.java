package com.stockmate.stockmate.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.stockmate.stockmate.dto.request.CreateProductRequest;
import com.stockmate.stockmate.dto.request.PlaceOrderRequest;
import com.stockmate.stockmate.dto.request.UpdateProductRequest;
import com.stockmate.stockmate.dto.response.CategoryResponse;
import com.stockmate.stockmate.dto.response.ProductResponse;
import com.stockmate.stockmate.security.CustomUserDetails;
import com.stockmate.stockmate.service.CategoryService;
import com.stockmate.stockmate.service.ProductService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * ProductController — product catalogue, detail, and CRUD endpoints.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * RESPONSIBILITY BOUNDARY (CLAUDE.md)
 * ══════════════════════════════════════════════════════════════════════════
 *   ✅ Maps URLs → service calls
 *   ✅ Extracts username from SecurityContext via @AuthenticationPrincipal
 *   ✅ Passes validated DTOs to ProductService
 *   ✅ Builds Pageable from request params for search/filter/sort
 *   ❌ Zero business logic — no stock calculation, no ownership checks
 *   ❌ Never touches a repository directly
 *   ❌ Never returns a JPA entity
 *
 * ENDPOINT SUMMARY  (FR-PROD-05/08/09/10 + FR-PROD-01/02/03/04)
 * ──────────────────────────────────────────────────────────────
 *   GET    /products            → paginated catalogue (all authenticated)
 *   GET    /products/{id}       → product detail page (all authenticated)
 *   GET    /products/new        → show create form (SELLER)
 *   POST   /products            → create product (SELLER)
 *   GET    /products/{id}/edit  → show edit form (SELLER/ADMIN)
 *   PUT    /products/{id}       → update product (SELLER own / ADMIN any)
 *   DELETE /products/{id}       → delete product (SELLER own / ADMIN any)
 *
 * NOTE ON HTML FORMS:
 *   Standard HTML forms only support GET and POST.  For PUT/DELETE, Thymeleaf
 *   uses a hidden _method field with Spring's HiddenHttpMethodFilter:
 *     <form method="post" th:action="@{/products/{id}(id=${product.id})}">
 *       <input type="hidden" name="_method" value="put"/>
 *   HiddenHttpMethodFilter must be enabled in application.properties:
 *     spring.mvc.hiddenmethod.filter.enabled=true
 */
@Slf4j
@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService  productService;
    private final CategoryService categoryService;

    // ── Constructor injection ─────────────────────────────────

    public ProductController(ProductService productService,
                             CategoryService categoryService) {
        this.productService  = productService;
        this.categoryService = categoryService;
    }

    // ══════════════════════════════════════════════════════════
    //  GET /products — paginated catalogue with search/filter/sort
    //  FR-PROD-05 / FR-PROD-08 / FR-PROD-09 / FR-PROD-10
    // ══════════════════════════════════════════════════════════

    /**
     * Renders the full product catalogue.
     *
     * Supports:
     *   ?keyword=   full-text search on name + description (FR-PROD-08)
     *   ?category=  filter by category ID (FR-PROD-09)
     *   ?sort=      "price_asc" | "price_desc" | "newest" (FR-PROD-10)
     *   ?page=      zero-based page number (default 0)
     *   ?size=      page size (default 12)
     *
     * The Pageable (sort + page + size) is built here in the controller and
     * passed to ProductService.getProducts() — sorting is presentation concern.
     */
    @GetMapping
    public String listProducts(
            @RequestParam(value = "keyword",  required = false) String keyword,
            @RequestParam(value = "category", required = false) Long   categoryId,
            @RequestParam(value = "sort",     defaultValue = "newest") String sort,
            @RequestParam(value = "page",     defaultValue = "0")      int    page,
            @RequestParam(value = "size",     defaultValue = "12")     int    size,
            Model model) {

        // ── Build sort from request param ─────────────────────
        Sort sortOrder = switch (sort) {
            case "price_asc"  -> Sort.by(Sort.Direction.ASC,  "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            default           -> Sort.by(Sort.Direction.DESC, "createdAt"); // "newest"
        };

        Pageable pageable = PageRequest.of(page, size, sortOrder);

        // ── Delegate to service ───────────────────────────────
        Page<ProductResponse> productPage =
                productService.getProducts(keyword, categoryId, pageable);

        // ── Load categories for filter dropdown ───────────────
        List<CategoryResponse> categories = categoryService.getAllCategories();

        model.addAttribute("products",      productPage.getContent());
        model.addAttribute("currentPage",   productPage.getNumber());
        model.addAttribute("totalPages",    productPage.getTotalPages());
        model.addAttribute("totalItems",    productPage.getTotalElements());
        model.addAttribute("categories",    categories);
        model.addAttribute("keyword",       keyword);
        model.addAttribute("selectedCat",   categoryId);
        model.addAttribute("sort",          sort);

        return "products/catalogue";   // → templates/products/catalogue.html
    }

    // ══════════════════════════════════════════════════════════
    //  GET /products/{id} — product detail page
    // ══════════════════════════════════════════════════════════

    /**
     * Shows the full detail page for a single product.
     * stockStatus is already computed inside the ProductResponse DTO —
     * the template just renders it.
     */
    @GetMapping("/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        ProductResponse product = productService.getProductById(id);
        model.addAttribute("product", product);
                if (!model.containsAttribute("placeOrderRequest")) {
                        model.addAttribute("placeOrderRequest", new PlaceOrderRequest(product.id(), 1));
                }
        return "products/detail";   // → templates/products/detail.html
    }

    // ══════════════════════════════════════════════════════════
    //  GET /products/new — show create form (SELLER only)
    //  URL-level guard: SecurityConfig restricts /products/new to SELLER
    // ══════════════════════════════════════════════════════════

    /**
     * Shows the product creation form.
     * Populates the category dropdown from CategoryService.
     */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("productRequest", new CreateProductRequest(
                "", "", null, 0, null));
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("formAction", "create");
        return "products/form";   // → templates/products/form.html
    }

    // ══════════════════════════════════════════════════════════
    //  POST /products — create product (SELLER)
    //  FR-PROD-01 / FR-PROD-11 / FR-PROD-12 / FR-PROD-13
    // ══════════════════════════════════════════════════════════

    /**
     * Processes product creation form submission.
     *
     * 1. @Valid enforces Bean Validation on the DTO
     * 2. BindingResult catches field errors → redisplay form
     * 3. Seller username extracted from SecurityContext via @AuthenticationPrincipal
     *    (never trusts a username from the request body — that would be a security hole)
     * 4. Delegates to ProductService.createProduct()
     * 5. On success → redirect to the new product's detail page
     */
    @PostMapping
    public String createProduct(
            @Valid @ModelAttribute("productRequest") CreateProductRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("formAction", "create");
            return "products/form";
        }

        ProductResponse created = productService.createProduct(
                request, currentUser.getUsername());

        log.info("Product created: id={} by seller={}",
                created.id(), currentUser.getUsername());

        redirectAttrs.addFlashAttribute("successMessage",
                "Product '" + created.name() + "' created successfully.");
        return "redirect:/products/" + created.id();
    }

    // ══════════════════════════════════════════════════════════
    //  GET /products/{id}/edit — show edit form (SELLER / ADMIN)
    //  URL-level guard: SecurityConfig restricts /products/*/edit to SELLER
    //  ADMIN access is added at method-level via @PreAuthorize in ProductServiceImpl
    // ══════════════════════════════════════════════════════════

    /**
     * Shows the edit form pre-populated with the product's current values.
     * ProductService.getProductById() will throw ResourceNotFoundException
     * if the product doesn't exist — GlobalExceptionHandler returns 404.
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id,
                               @AuthenticationPrincipal CustomUserDetails currentUser,
                               Model model) {

        ProductResponse product = productService.getProductById(id);

        // Pre-populate the UpdateProductRequest from current product values
        UpdateProductRequest updateRequest = new UpdateProductRequest(
                product.name(),
                product.description(),
                product.price(),
                product.stockQuantity(),
                com.stockmate.stockmate.model.enums.ProductStatus.valueOf(product.status()),
                product.categoryId()
        );

        model.addAttribute("productRequest", updateRequest);
        model.addAttribute("product",        product);
        model.addAttribute("categories",     categoryService.getAllCategories());
        model.addAttribute("formAction",     "edit");
        return "products/form";
    }

    // ══════════════════════════════════════════════════════════
    //  PUT /products/{id} — update product (SELLER own / ADMIN any)
    //  FR-PROD-02 / FR-PROD-03 / FR-PROD-04
    //
    //  HTML workaround: <input type="hidden" name="_method" value="put"/>
    //  spring.mvc.hiddenmethod.filter.enabled=true must be set.
    // ══════════════════════════════════════════════════════════

    /**
     * Processes the product edit form.
     * Ownership enforcement lives in ProductServiceImpl.assertOwnershipOrAdmin(),
     * which throws AccessDeniedException if a SELLER tries to edit another's product.
     * GlobalExceptionHandler catches that and renders the 403 page.
     */
    @PutMapping("/{id}")
    public String updateProduct(
            @PathVariable Long id,
            @Valid @ModelAttribute("productRequest") UpdateProductRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttrs) {

        if (bindingResult.hasErrors()) {
            ProductResponse product = productService.getProductById(id);
            model.addAttribute("product",    product);
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("formAction", "edit");
            return "products/form";
        }

        ProductResponse updated = productService.updateProduct(
                id, request, currentUser.getUsername());

        log.info("Product updated: id={} by user={}",
                updated.id(), currentUser.getUsername());

        redirectAttrs.addFlashAttribute("successMessage",
                "Product '" + updated.name() + "' updated successfully.");
        return "redirect:/products/" + updated.id();
    }

    // ══════════════════════════════════════════════════════════
    //  DELETE /products/{id} — delete product (SELLER own / ADMIN any)
    //  FR-PROD-02 / FR-PROD-04
    //
    //  HTML workaround: <input type="hidden" name="_method" value="delete"/>
    // ══════════════════════════════════════════════════════════

    /**
     * Deletes a product.
     * AccessDeniedException from ProductServiceImpl (SELLER deleting another's product)
     * is handled by GlobalExceptionHandler → 403 page.
     */
    @DeleteMapping("/{id}")
    public String deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttrs) {

        productService.deleteProduct(id, currentUser.getUsername());

        log.info("Product deleted: id={} by user={}", id, currentUser.getUsername());

        redirectAttrs.addFlashAttribute("successMessage",
                "Product deleted successfully.");
        return "redirect:/products";
    }
}
