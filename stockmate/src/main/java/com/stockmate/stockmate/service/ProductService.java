package com.stockmate.stockmate.service;


import com.stockmate.stockmate.dto.request.CreateProductRequest;
import com.stockmate.stockmate.dto.request.UpdateProductRequest;
import com.stockmate.stockmate.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * ProductService — core product-catalogue business rules.
 *
 * Covers: CRUD (SELLER/ADMIN), catalogue browsing with search / filter / sort
 * (all authenticated), and computed stock-status logic (never stored in DB).
 *
 * Stock status derivation (FR-PROD-14) lives here — computed in toResponse(),
 * never persisted.
 */
public interface ProductService {

    // ── Catalogue (all authenticated, FR-PROD-05/08/09/10) ───

    /**
     * Paginated catalogue with optional keyword + category filter and sorting.
     *
     * Inputs     : keyword (nullable), categoryId (nullable), Pageable (sort + page)
     * Validation : none — empty keyword/categoryId means "all"
     * Repos      : ProductRepository (searchByKeyword / findByCategoryId /
     *              searchByKeywordAndCategory / findAllWithDetails)
     * Business   : chooses the right query branch; computes stockStatus per product
     * Output     : Page<ProductResponse> (includes computed stockStatus)
     */
    Page<ProductResponse> getProducts(String keyword, Long categoryId, Pageable pageable);

    /**
     * Single product detail.
     *
     * Inputs     : id (Long)
     * Validation : throws ResourceNotFoundException if absent
     * Repos      : ProductRepository.findById()
     * Business   : computes stockStatus
     * Output     : ProductResponse
     */
    ProductResponse getProductById(Long id);

    /**
     * All products owned by a specific seller — used by seller dashboard.
     *
     * Inputs     : sellerUsername (String)
     * Validation : seller user must exist
     * Repos      : UserRepository (load seller) + ProductRepository.findBySeller()
     * Business   : computes stockStatus per product
     * Output     : List<ProductResponse>
     */
    List<ProductResponse> getProductsBySeller(String sellerUsername);

    // ── Write (SELLER / ADMIN) ────────────────────────────────

    /**
     * Create a new product — SELLER only (FR-PROD-01).
     *
     * Inputs     : CreateProductRequest, sellerUsername (from security context)
     * Validation : category must exist · @PreAuthorize SELLER
     * Repos      : CategoryRepository (via findById) · UserRepository · ProductRepository.save()
     * Business   : seller auto-assigned; status defaults to ACTIVE
     * Output     : ProductResponse
     */
    ProductResponse createProduct(CreateProductRequest request, String sellerUsername);

    /**
     * Update a product — SELLER (own) or ADMIN (any) (FR-PROD-02/04).
     *
     * Inputs     : id, UpdateProductRequest, currentUsername (from security context)
     * Validation : product must exist · ownership: SELLER can only edit their own ·
     *              ADMIN may edit any · @PreAuthorize SELLER or ADMIN
     * Repos      : ProductRepository · UserRepository · CategoryRepository
     * Business   : ownership check with AccessDeniedException on violation
     * Output     : updated ProductResponse
     */
    ProductResponse updateProduct(Long id, UpdateProductRequest request, String currentUsername);

    /**
     * Delete a product — SELLER (own) or ADMIN (any) (FR-PROD-02/04).
     *
     * Inputs     : id, currentUsername (from security context)
     * Validation : product must exist · ownership check (same as update)
     * Repos      : ProductRepository · UserRepository
     * Business   : AccessDeniedException if SELLER tries to delete another's product
     * Output     : void
     */
    void deleteProduct(Long id, String currentUsername);

    // ── Stock-status helper (also used by OrderService indirectly) ──

    /**
     * Compute the display stock status string from stored fields.
     * Called internally by toResponse() — exposed on interface so it can
     * be tested independently in ProductServiceTest.
     *
     * Inputs     : stockQuantity (int), productStatus (ProductStatus)
     * Validation : none
     * Business   : FR-PROD-14 rule:
     *              qty > 0          → "In Stock"
     *              qty = 0, ACTIVE  → "Pre Order"
     *              qty = 0, DISC.   → "Out of Stock"
     * Output     : String (display label)
     */
    String computeStockStatus(int stockQuantity,
                              com.stockmate.stockmate.model.enums.ProductStatus productStatus);
}