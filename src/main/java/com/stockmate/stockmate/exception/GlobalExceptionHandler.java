package com.stockmate.stockmate.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — the single centralised exception handler for
 * all StockMate controllers.
 *
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  WHY @ControllerAdvice (not @RestControllerAdvice)?                 │
 * │                                                                      │
 * │  StockMate uses Thymeleaf (server-side rendered HTML), not a REST   │
 * │  JSON API. @ControllerAdvice returns view names (Strings) that      │
 * │  Thymeleaf resolves to HTML templates, with error details added to  │
 * │  the Model. @RestControllerAdvice would force JSON responses,       │
 * │  breaking the UI.                                                    │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * EXCEPTION → HTTP STATUS MAP  (as specified in CLAUDE.md)
 * ──────────────────────────────────────────────────────────
 *   ResourceNotFoundException          → 404 Not Found
 *   AccessDeniedException              → 403 Forbidden
 *   InsufficientStockException         → 400 Bad Request
 *   MethodArgumentNotValidException    → 400 Bad Request (field-level errors)
 *   IllegalArgumentException           → 400 Bad Request (duplicate name, bad role)
 *   IllegalStateException              → 409 Conflict  (delete category with products;
 *                                                        cancel terminal order)
 *   Generic Exception                  → 500 Internal Server Error
 *
 * TEMPLATE CONTRACT
 * ─────────────────
 * Each handler adds attributes to the Model and returns a Thymeleaf
 * view name. The templates must exist at:
 *   error/404.html
 *   error/403.html
 *   error/400.html
 *   error/409.html
 *   error/500.html
 *
 * Each template reads:  ${errorTitle}, ${errorMessage}, ${errors} (for 400)
 *
 * NOTE on AccessDeniedException from Spring Security URL-level rules:
 * When Spring Security itself blocks a URL (SecurityFilterChain layer),
 * it does NOT go through @ControllerAdvice — it uses the accessDeniedPage()
 * configured in SecurityConfig (/error/403).
 * This handler only catches AccessDeniedException thrown by @PreAuthorize
 * in the service layer (method-level security, layer 2).
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────

    /**
     * Handles ResourceNotFoundException.
     *
     * THROWN BY
     *   RoleServiceImpl      — role seed row missing
     *   UserServiceImpl      — user not found by id or username
     *   CategoryServiceImpl  — category not found by id
     *   ProductServiceImpl   — product not found by id, or category not found
     *   OrderServiceImpl     — product not found (place order), order not found
     *
     * RESPONSE
     *   HTTP 404, renders error/404.html
     *   Model attributes: errorTitle, errorMessage
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        log.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("errorTitle",   "Not Found");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/404";
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────

    /**
     * Handles AccessDeniedException from @PreAuthorize (method-level security).
     *
     * THROWN BY
     *   ProductServiceImpl.assertOwnershipOrAdmin()
     *     — SELLER tries to edit/delete another seller's product
     *   OrderServiceImpl.cancelOrder()
     *     — BUYER tries to cancel an order that belongs to another buyer
     *   OrderServiceImpl.updateOrderStatus()
     *     — SELLER tries to update status of an order on another seller's product
     *   Any @PreAuthorize annotation violation (wrong role called the method)
     *
     * NOTE: URL-level 403s (from SecurityFilterChain) use accessDeniedPage()
     * in SecurityConfig and do NOT pass through here.
     *
     * RESPONSE
     *   HTTP 403, renders error/403.html
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        log.warn("Access denied: {}", ex.getMessage());
        model.addAttribute("errorTitle",   "Access Denied");
        model.addAttribute("errorMessage",
                "You do not have permission to perform this action.");
        return "error/403";
    }

    // ── 400 Bad Request — InsufficientStock ───────────────────────────────

    /**
     * Handles InsufficientStockException.
     *
     * THROWN BY
     *   OrderServiceImpl.placeOrder()
     *     — BUYER requests more units than product.stockQuantity
     *     — Test: OrderServiceTest.placeOrder_insufficientStock()
     *
     * RESPONSE
     *   HTTP 400, renders error/400.html with user-friendly stock message
     */
    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInsufficientStock(InsufficientStockException ex, Model model) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        model.addAttribute("errorTitle",   "Insufficient Stock");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/400";
    }

    // ── 400 Bad Request — Validation (@Valid on DTOs) ─────────────────────

    /**
     * Handles MethodArgumentNotValidException — triggered by @Valid on
     * controller method parameters (e.g. @Valid CreateProductRequest).
     *
     * THROWN BY
     *   Any controller method that uses @Valid on a request DTO, including:
     *     AuthController    — RegisterRequest, LoginRequest
     *     ProductController — CreateProductRequest, UpdateProductRequest
     *     OrderController   — PlaceOrderRequest, UpdateOrderStatusRequest
     *     CategoryController— CreateCategoryRequest
     *
     * RESPONSE
     *   HTTP 400, renders error/400.html
     *   Model attribute "errors" = Map<fieldName, errorMessage> for
     *   Thymeleaf to display field-level validation messages in the form.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleValidationErrors(MethodArgumentNotValidException ex, Model model) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);
        model.addAttribute("errorTitle",   "Validation Error");
        model.addAttribute("errorMessage", "Please correct the highlighted fields.");
        model.addAttribute("errors",       fieldErrors);
        return "error/400";
    }

    // ── 400 Bad Request — IllegalArgument (duplicate names, bad role) ──────

    /**
     * Handles IllegalArgumentException — thrown for business-rule violations
     * that are the caller's fault (bad input).
     *
     * THROWN BY
     *   UserServiceImpl.register()
     *     — duplicate username: "Username already taken: alice"
     *     — duplicate email:    "Email already registered: alice@example.com"
     *     — ADMIN role self-assign attempt
     *   CategoryServiceImpl.createCategory()
     *     — duplicate category name
     *   CategoryServiceImpl.updateCategory()
     *     — name conflict with another category
     *
     * RESPONSE
     *   HTTP 400, renders error/400.html
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException ex, Model model) {
        log.warn("Illegal argument: {}", ex.getMessage());
        model.addAttribute("errorTitle",   "Bad Request");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/400";
    }

    // ── 409 Conflict — IllegalState ───────────────────────────────────────

    /**
     * Handles IllegalStateException — thrown when an operation is logically
     * impossible given the current system state.
     *
     * THROWN BY
     *   CategoryServiceImpl.deleteCategory()
     *     — category still has products assigned (FK safety)
     *   OrderServiceImpl.cancelOrder()
     *     — order is already DELIVERED or CANCELLED (terminal state)
     *   OrderServiceImpl.updateOrderStatus()
     *     — order is in a terminal state (DELIVERED / CANCELLED)
     *
     * RESPONSE
     *   HTTP 409 Conflict (state violation is a conflict, not a bad input)
     *   renders error/409.html
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleIllegalState(IllegalStateException ex, Model model) {
        log.warn("Illegal state: {}", ex.getMessage());
        model.addAttribute("errorTitle",   "Conflict");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/409";
    }

    // ── 500 Internal Server Error — catch-all ─────────────────────────────

    /**
     * Catch-all for any unhandled exception.
     *
     * THROWN BY
     *   Anything unexpected — DB connection failure, NullPointerException,
     *   Hibernate constraint violations that slipped through, etc.
     *
     * RESPONSE
     *   HTTP 500, renders error/500.html
     *   Error details are logged at ERROR level but NOT shown to the user
     *   (avoid leaking stack traces / DB schema to the browser).
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        model.addAttribute("errorTitle",   "Internal Server Error");
        model.addAttribute("errorMessage",
                "Something went wrong on our end. Please try again later.");
        return "error/500";
    }
}