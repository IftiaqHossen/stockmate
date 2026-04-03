package com.stockmate.stockmate.exception;


import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ResourceNotFoundException — thrown when a requested entity does not exist in the DB.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  WHERE IS IT THROWN?                                             │
 * │                                                                  │
 * │  RoleServiceImpl.findByName()                                    │
 * │    → Role seed row missing from DB (programmer/ops error)        │
 * │                                                                  │
 * │  UserServiceImpl.findUserOrThrow()                               │
 * │    → Admin tries to change role/disable a userId that doesn't    │
 * │      exist; or security layer loads a deleted user               │
 * │                                                                  │
 * │  CategoryServiceImpl.findOrThrow()                               │
 * │    → Admin tries to edit/delete a category that doesn't exist    │
 * │    → ProductService tries to validate a categoryId that is gone  │
 * │                                                                  │
 * │  ProductServiceImpl.findProductOrThrow()                         │
 * │    → GET /products/{id} for a deleted product                    │
 * │    → SELLER tries to edit/delete a product that doesn't exist    │
 * │                                                                  │
 * │  ProductServiceImpl.findCategoryOrThrow()                        │
 * │    → SELLER creates/updates a product with a non-existent        │
 * │      categoryId (bad form submission)                            │
 * │                                                                  │
 * │  OrderServiceImpl.findProductOrThrow()                           │
 * │    → BUYER tries to order a product that has been deleted        │
 * │      (placeOrder_productNotFound test case)                      │
 * │                                                                  │
 * │  OrderServiceImpl.findOrderOrThrow()                             │
 * │    → SELLER/ADMIN tries to update status of a non-existent order │
 * │                                                                  │
 * │  WHERE IS IT CAUGHT?                                             │
 * │    GlobalExceptionHandler.handleResourceNotFound()               │
 * │    → returns HTTP 404 + JSON/view error body                     │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * Extends RuntimeException — no checked-exception boilerplate in callers.
 * @ResponseStatus tells Spring MVC the HTTP status when no handler is present,
 * but GlobalExceptionHandler takes precedence and handles it explicitly.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @param message human-readable description, e.g.
     *                "Product not found with id: 42"
     *                "Category not found with id: 7"
     *                "User not found: alice"
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructor with cause — useful when wrapping a lower-level exception
     * while preserving the original stack trace.
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}