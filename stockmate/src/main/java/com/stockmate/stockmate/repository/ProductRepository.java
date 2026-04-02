package com.stockmate.stockmate.repository;

import com.stockmate.stockmate.model.Product;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.model.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the products table.
 *
 * Queries needed by the service layer:
 *
 *  ProductService (catalogue — FR-PROD-05/08/09/10):
 *    - findAll with search by name keyword      → searchByKeyword()
 *    - filter by category                       → findByCategoryId()
 *    - seller's own product list               → findBySeller()
 *    - single product detail                   → findById() [inherited]
 *    - ownership check (seller editing own)    → findByIdAndSeller()
 *
 *  OrderService (FR-ORD-02/03):
 *    - load product before placing order        → findById() [inherited]
 *
 *  AdminService:
 *    - all products system-wide               → findAll() [inherited]
 *
 * Sorting (price asc/desc, newest) is handled via Pageable — no extra methods needed.
 * No business logic here — just query declarations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ── Catalogue queries (FR-PROD-08/09/10) ──────────────────

    /**
     * Full-text keyword search across product name and description.
     * Case-insensitive via LOWER(). Used by ProductService.search().
     *
     * Returns a Page so the controller can apply sorting (Pageable
     * carries both page info and Sort direction for price/date).
     * JOIN FETCH seller and category avoids N+1 when building ProductResponse DTOs.
     */
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.seller
            JOIN FETCH p.category
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<Product> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Filter products by category id (FR-PROD-09).
     * JOIN FETCH avoids N+1 for seller and category when building DTOs.
     */
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.seller
            JOIN FETCH p.category
            WHERE p.category.id = :categoryId
            """)
    Page<Product> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    /**
     * Combined search + category filter.
     * Used when the user applies both a keyword and a category dropdown selection.
     */
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.seller
            JOIN FETCH p.category
            WHERE p.category.id = :categoryId
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR  LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Product> searchByKeywordAndCategory(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            Pageable pageable);

    /**
     * Full catalogue with no filters — used when keyword and category are both empty.
     * JOIN FETCH prevents N+1 selects on seller and category.
     */
    @Query(value = """
            SELECT p FROM Product p
            JOIN FETCH p.seller
            JOIN FETCH p.category
            """,
            countQuery = "SELECT COUNT(p) FROM Product p")
    Page<Product> findAllWithDetails(Pageable pageable);

    // ── Seller-scoped queries ──────────────────────────────────

    /**
     * Seller dashboard: list only this seller's own products (FR-PROD-02).
     * Used by ProductService.getProductsBySeller().
     */
    List<Product> findBySeller(User seller);

    /**
     * Ownership check before edit/delete (FR-PROD-03).
     * Returns empty if the product exists but belongs to a different seller.
     * ProductServiceImpl throws AccessDeniedException on empty result.
     */
    Optional<Product> findByIdAndSeller(Long id, User seller);

    // ── Status queries ─────────────────────────────────────────

    /**
     * Find all products by status — used if Admin wants to view
     * DISCONTINUED products separately (useful for admin dashboard).
     */
    List<Product> findByStatus(ProductStatus status);

    /**
     * Count products owned by a seller — used for seller dashboard stats.
     */
    long countBySeller(User seller);
}