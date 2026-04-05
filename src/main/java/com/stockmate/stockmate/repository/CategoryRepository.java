package com.stockmate.stockmate.repository;

import com.stockmate.stockmate.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for the categories table.
 *
 * Queries needed by the service layer:
 *  - CategoryService.create()   → existsByName()    [duplicate name guard]
 *  - CategoryService.getAll()   → findAll()         [dropdown list for product form]
 *  - CategoryService.getById()  → findById()        [edit/delete by Admin]
 *  - CategoryService.delete()   → hasProducts()     [block delete if products exist]
 *  - ProductService.create()    → findById()        [validate category exists]
 *
 * No business logic here — just query declarations.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Guard against duplicate category names before attempting INSERT.
     * Called in CategoryService.create() to produce a clean error message
     * rather than a DB constraint violation.
     */
    boolean existsByName(String name);

    /**
     * Find by name — used when checking for duplicates on update
     * (need to exclude the current category's own name).
     */
    Optional<Category> findByName(String name);

    /**
     * Check whether any products reference this category before deleting it.
     * Prevents orphaned products or FK constraint violations.
     * Called by CategoryService.delete() to throw a meaningful exception.
     *
     * COUNT is cheaper than fetching the full product list.
     */
    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.category.id = :categoryId")
    boolean hasProducts(Long categoryId);
}