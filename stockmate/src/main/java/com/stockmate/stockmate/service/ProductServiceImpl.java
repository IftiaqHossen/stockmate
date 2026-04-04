package com.stockmate.stockmate.service;

import com.stockmate.stockmate.dto.request.CreateProductRequest;
import com.stockmate.stockmate.dto.request.UpdateProductRequest;
import com.stockmate.stockmate.dto.response.ProductResponse;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Category;
import com.stockmate.stockmate.model.Product;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.model.enums.ProductStatus;
import com.stockmate.stockmate.repository.CategoryRepository;
import com.stockmate.stockmate.repository.ProductRepository;
import com.stockmate.stockmate.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ProductServiceImpl — product catalogue rules and stock-status computation.
 *
 * Dependencies (constructor-injected, final):
 *   ProductRepository  — product CRUD
 *   CategoryRepository — category lookup for validation
 *   UserRepository     — seller lookup for ownership checks
 *
 * Coupling check:
 *   ✅ No reference to OrderService (no circular dep)
 *   ✅ No reference to UserService (goes direct to UserRepository for the entity)
 *   ✅ Stock status computed here, never stored
 */
@Slf4j
@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository  productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository     userRepository;

    // ── Constructor injection ─────────────────────────────────

    public ProductServiceImpl(ProductRepository productRepository,
                              CategoryRepository categoryRepository,
                              UserRepository userRepository) {
        this.productRepository  = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository     = userRepository;
    }

    // ── Catalogue ─────────────────────────────────────────────

    /**
     * getProducts
     *
     * Inputs     : keyword (nullable), categoryId (nullable), Pageable
     * Validation : none (open to all authenticated users)
     * Repos      : ProductRepository — 4 query branches
     * Business   : branch on whether keyword/categoryId are present;
     *              compute stockStatus for each product in result
     * Output     : Page<ProductResponse>
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(String keyword, Long categoryId, Pageable pageable) {
        log.debug("Catalogue query: keyword='{}', categoryId={}", keyword, categoryId);

        boolean hasKeyword   = keyword   != null && !keyword.isBlank();
        boolean hasCategory  = categoryId != null;

        Page<Product> page;

        if (hasKeyword && hasCategory) {
            page = productRepository.searchByKeywordAndCategory(keyword, categoryId, pageable);
        } else if (hasKeyword) {
            page = productRepository.searchByKeyword(keyword, pageable);
        } else if (hasCategory) {
            page = productRepository.findByCategoryId(categoryId, pageable);
        } else {
            page = productRepository.findAllWithDetails(pageable);
        }

        return page.map(this::toResponse);
    }

    /**
     * getProductById
     *
     * Inputs     : id
     * Validation : throws ResourceNotFoundException if absent
     * Repos      : ProductRepository.findById()
     * Business   : compute stockStatus
     * Output     : ProductResponse
     */
    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        log.debug("Fetching product id={}", id);
        Product product = findProductOrThrow(id);
        return toResponse(product);
    }

    /**
     * getProductsBySeller
     *
     * Inputs     : sellerUsername
     * Validation : seller must exist in DB
     * Repos      : UserRepository + ProductRepository.findBySeller()
     * Business   : compute stockStatus per product
     * Output     : List<ProductResponse>
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsBySeller(String sellerUsername) {
        log.debug("Fetching products for seller: {}", sellerUsername);
        User seller = findUserOrThrow(sellerUsername);
        return productRepository.findBySeller(seller)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Write ─────────────────────────────────────────────────

    /**
     * createProduct
     *
     * Inputs     : CreateProductRequest, sellerUsername (from Authentication)
     * Validation : category must exist · SELLER only
     * Repos      : CategoryRepository + UserRepository + ProductRepository.save()
     * Business   : seller auto-assigned from security context; status → ACTIVE
     * Output     : ProductResponse
     */
    @Override
    @PreAuthorize("hasRole('SELLER')")
    public ProductResponse createProduct(CreateProductRequest request, String sellerUsername) {
        log.info("Seller '{}' creating product '{}'", sellerUsername, request.name());

        Category category = findCategoryOrThrow(request.categoryId());
        User seller       = findUserOrThrow(sellerUsername);

        Product product = new Product(
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                category,
                seller
        );

        Product saved = productRepository.save(product);
        log.info("Product created: id={}, seller={}", saved.getId(), sellerUsername);
        return toResponse(saved);
    }

    /**
     * updateProduct
     *
     * Inputs     : id, UpdateProductRequest, currentUsername
     * Validation : product must exist · ownership (SELLER = own only; ADMIN = any)
     * Repos      : ProductRepository + CategoryRepository + UserRepository
     * Business   : ownership check throws AccessDeniedException on violation
     * Output     : updated ProductResponse
     */
    @Override
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ProductResponse updateProduct(Long id, UpdateProductRequest request,
                                         String currentUsername) {
        log.info("User '{}' updating product id={}", currentUsername, id);

        Product product = findProductOrThrow(id);
        assertOwnershipOrAdmin(product, currentUsername);

        // Apply updates
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setStatus(request.status());

        if (request.categoryId() != null) {
            Category category = findCategoryOrThrow(request.categoryId());
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);
        log.info("Product updated: id={}", saved.getId());
        return toResponse(saved);
    }

    /**
     * deleteProduct
     *
     * Inputs     : id, currentUsername
     * Validation : product must exist · ownership check (same as update)
     * Repos      : ProductRepository
     * Business   : AccessDeniedException if SELLER owns another seller's product
     * Output     : void
     */
    @Override
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public void deleteProduct(Long id, String currentUsername) {
        log.info("User '{}' deleting product id={}", currentUsername, id);

        Product product = findProductOrThrow(id);
        assertOwnershipOrAdmin(product, currentUsername);

        productRepository.delete(product);
        log.info("Product deleted: id={}", id);
    }

    // ── Stock status computation (FR-PROD-14) ─────────────────

    /**
     * computeStockStatus
     *
     * Inputs     : stockQuantity, productStatus
     * Business   : pure function — no DB calls, no side effects
     *              qty > 0          → "In Stock"
     *              qty = 0, ACTIVE  → "Pre Order"
     *              qty = 0, DISC.   → "Out of Stock"
     * Output     : display String
     */
    @Override
    public String computeStockStatus(int stockQuantity, ProductStatus productStatus) {
        if (stockQuantity > 0) {
            return "In Stock";
        }
        return productStatus == ProductStatus.ACTIVE ? "Pre Order" : "Out of Stock";
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Ownership guard — SELLER may only touch their own products.
     * ADMIN bypasses the check entirely.
     * Throws AccessDeniedException (→ 403) on violation.
     */
    private void assertOwnershipOrAdmin(Product product, String currentUsername) {
        User currentUser = findUserOrThrow(currentUsername);

        boolean isAdmin = currentUser.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_ADMIN"));

        if (isAdmin) return;  // Admin has blanket permission

        boolean isOwner = product.getSeller().getUsername().equals(currentUsername);
        if (!isOwner) {
            log.warn("Access denied: user '{}' tried to modify product id={} owned by '{}'",
                    currentUsername, product.getId(), product.getSeller().getUsername());
            throw new AccessDeniedException(
                    "You do not have permission to modify this product.");
        }
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + categoryId));
    }

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));
    }

    /**
     * Entity → DTO mapping.
     * stockStatus is computed here — never read from the DB.
     * Entities are confined to this service; the DTO crosses the boundary.
     */
    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStockQuantity(),
                computeStockStatus(p.getStockQuantity(), p.getStatus()),   // computed!
                p.getStatus().name(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getSeller().getId(),
                p.getSeller().getUsername(),
                p.getCreatedAt()
        );
    }
}
