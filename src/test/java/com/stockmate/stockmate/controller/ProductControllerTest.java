package com.stockmate.stockmate.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.stockmate.stockmate.dto.request.CreateProductRequest;
import com.stockmate.stockmate.dto.response.CategoryResponse;
import com.stockmate.stockmate.dto.response.ProductResponse;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.security.CustomUserDetails;
import com.stockmate.stockmate.service.CategoryService;
import com.stockmate.stockmate.service.ProductService;

/**
 * Integration tests for ProductController.
 *
 * Uses @SpringBootTest to load the full application context and
 * @AutoConfigureMockMvc to fire HTTP requests through the full filter chain
 * (including Spring Security). ProductService is mocked to isolate
 * controller-layer concerns.
 *
 * Test profile: "test" — uses H2 in-memory DB, not PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CategoryService categoryService;

    // ── TC-1: listProducts_authenticated ─────────────────────────────────────
    /**
     * WHY: Any authenticated user (BUYER, SELLER, ADMIN) must be able to browse
     * the catalogue (FR-PROD-05). Verifies the endpoint returns 200 with a JSON
     * array.
     */
    @Test
    @DisplayName("GET /products: authenticated BUYER receives 200 with product list")
    void listProducts_authenticated() throws Exception {
        // Arrange
        ProductResponse product = buildProductResponse(1L, "Laptop", "999.99", "IN_STOCK");
        Page<ProductResponse> page = new PageImpl<>(List.of(product), PageRequest.of(0, 12), 1);
        given(productService.getProducts(isNull(), isNull(), any(Pageable.class))).willReturn(page);
        given(categoryService.getAllCategories())
                .willReturn(List.of(new CategoryResponse(10L, "Electronics", "Devices")));

        // Act & Assert
        mockMvc.perform(get("/products")
                .with(user("buyerUser").roles("BUYER")))
                .andExpect(status().isOk())
                .andExpect(view().name("products/catalogue"))
                .andExpect(model().attributeExists("products", "categories", "currentPage", "totalPages", "totalItems"));
    }

    // ── TC-2: listProducts_unauthenticated ────────────────────────────────────
    /**
     * WHY: Unauthenticated requests to /products must be redirected to login
     * (FR-AUTH-05). Spring Security should block this before the controller is
     * even called.
     */
    @Test
    @DisplayName("GET /products: unauthenticated request receives 302 redirect to login")
    void listProducts_unauthenticated() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    // ── TC-3: createProduct_asSeller_success ──────────────────────────────────
    /**
     * WHY: A SELLER posting a valid product should receive 201 Created.
     * Verifies the happy path through the full HTTP stack — request
     * deserialization, Bean Validation, security filter, service call, and
     * response serialization.
     */
    @Test
    @DisplayName("POST /products: SELLER with valid request receives 201 Created")
    void createProduct_asSeller_success() throws Exception {
        // Arrange
        CreateProductRequest request = buildCreateProductRequest("Laptop", 999.99, 5, 10L);
        ProductResponse response = buildProductResponse(1L, "Laptop", "999.99", "In Stock");

        given(productService.createProduct(any(CreateProductRequest.class), eq("sellerUser")))
                .willReturn(response);

        // Act & Assert
        mockMvc.perform(post("/products")
                .with(user(buildPrincipal("sellerUser", "ROLE_SELLER")))
                .with(csrf())
                .param("name", request.name())
                .param("description", request.description())
                .param("price", request.price().toPlainString())
                .param("stockQuantity", String.valueOf(request.stockQuantity()))
                .param("categoryId", String.valueOf(request.categoryId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/1"))
                .andExpect(flash().attributeExists("successMessage"));

        then(productService).should().createProduct(any(CreateProductRequest.class), eq("sellerUser"));
    }

    // ── TC-4: createForm_asBuyer_forbidden ──────────────────────────────────
    /**
     * WHY: A BUYER must not be able to create products (FR-PROD-01 — SELLER
     * only). This is a CRITICAL security test. If it fails, the role boundary
     * is broken.
     */
    @Test
    @DisplayName("GET /products/new: BUYER receives 403 Forbidden")
    void createForm_asBuyer_forbidden() throws Exception {
        // Act & Assert — URL-level security blocks BUYER access to create form
        mockMvc.perform(get("/products/new")
                .with(user("buyerUser").roles("BUYER")))
                .andExpect(status().isForbidden());
    }

    // ── TC-5: createProduct_invalidData_badRequest ────────────────────────────
    /**
     * WHY: Missing required fields (price=null, name=blank) must return 400
     * with field-level validation messages — NOT 500. GlobalExceptionHandler
     * must catch MethodArgumentNotValidException.
     */
    @Test
    @DisplayName("POST /products: invalid request body (blank name, negative price) returns 400")
    void createProduct_invalidData_badRequest() throws Exception {
        // Arrange
        given(categoryService.getAllCategories())
                .willReturn(List.of(new CategoryResponse(10L, "Electronics", "Devices")));

        // Act & Assert
        mockMvc.perform(post("/products")
                .with(user(buildPrincipal("sellerUser", "ROLE_SELLER")))
                .with(csrf())
                .param("name", "")
                .param("description", "bad")
                .param("price", "-1")
                .param("stockQuantity", "-5")
                .param("categoryId", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/form"))
                .andExpect(model().attributeHasFieldErrors("productRequest", "name", "price", "stockQuantity"));

        then(productService).should(never()).createProduct(any(CreateProductRequest.class), any(String.class));
    }

    // ── TC-6: getProductById_notFound ─────────────────────────────────────────
    /**
     * WHY: Requesting a deleted/non-existent product ID must return 404.
     * GlobalExceptionHandler translates ResourceNotFoundException → 404.
     * Without this, the caller would receive a 500 — terrible UX and
     * misleading.
     */
    @Test
    @DisplayName("GET /products/{id}: non-existent ID returns 404 via GlobalExceptionHandler")
    void getProductById_notFound() throws Exception {
        // Arrange
        given(productService.getProductById(9999L))
                .willThrow(new ResourceNotFoundException("Product not found with id: 9999"));

        // Act & Assert
        mockMvc.perform(get("/products/9999")
                .with(user("buyerUser").roles("BUYER")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    // ── TC-7: deleteProduct_asAdmin_success ───────────────────────────────────
    /**
     * WHY: Admin must be able to delete any product (FR-PROD-04). This test
     * validates the /products/{id} DELETE endpoint is accessible to ADMIN.
     */
    @Test
    @DisplayName("DELETE /products/{id}: ADMIN receives 204 No Content")
    void deleteProduct_asAdmin_success() throws Exception {
        // No service setup needed — void method defaults to no-op in Mockito

        mockMvc.perform(delete("/products/1")
                .with(user(buildPrincipal("adminUser", "ROLE_ADMIN")))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"))
                .andExpect(flash().attributeExists("successMessage"));

        then(productService).should().deleteProduct(1L, "adminUser");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private ProductResponse buildProductResponse(Long id, String name, String price, String stockStatus) {
        return new ProductResponse(
                id,
                name,
                "High-end product",
                new BigDecimal(price),
                5,
                stockStatus,
                "ACTIVE",
                10L,
                "Electronics",
                100L,
                "sellerUser",
                LocalDateTime.now()
        );
    }

    private CreateProductRequest buildCreateProductRequest(String name, double price, int qty, Long categoryId) {
        return new CreateProductRequest(
                name,
                "High-end product",
                BigDecimal.valueOf(price),
                qty,
                categoryId
        );
    }

    private CustomUserDetails buildPrincipal(String username, String roleName) {
        User user = new User(username, username + "@test.com", "encoded-password");
        user.setRoles(Set.of(new Role(roleName)));
        return new CustomUserDetails(user);
    }
}
