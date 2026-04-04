package com.stockmate.stockmate.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.stockmate.stockmate.dto.request.CreateProductRequest;
import com.stockmate.stockmate.dto.request.UpdateProductRequest;
import com.stockmate.stockmate.dto.response.ProductResponse;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Category;
import com.stockmate.stockmate.model.Product;
import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.model.enums.ProductStatus;
import com.stockmate.stockmate.repository.CategoryRepository;
import com.stockmate.stockmate.repository.ProductRepository;
import com.stockmate.stockmate.repository.UserRepository;

/**
 * Unit tests for ProductServiceImpl.
 *
 * No Spring context is loaded — all dependencies are mocked with Mockito. Tests
 * focus on business logic correctness, ownership enforcement, and stock-status
 * computation which are core to the StockMate domain.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("999.99");

    @Test
    @DisplayName("createProduct: valid request by seller returns ProductResponse")
    void createProduct_success() {
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product saved = product("Laptop", "A powerful laptop", DEFAULT_PRICE, 5,
                ProductStatus.ACTIVE, category, seller);

        CreateProductRequest request = new CreateProductRequest(
                "Laptop", "A powerful laptop", DEFAULT_PRICE, 5, 10L);

        given(categoryRepository.findById(10L)).willReturn(Optional.of(category));
        given(userRepository.findByUsername("sellerUser")).willReturn(Optional.of(seller));
        given(productRepository.save(any(Product.class))).willReturn(saved);

        ProductResponse response = productService.createProduct(request, "sellerUser");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Laptop");
        assertThat(response.price()).isEqualByComparingTo(DEFAULT_PRICE);
        assertThat(response.categoryName()).isEqualTo("Electronics");
        assertThat(response.stockStatus()).isEqualTo("In Stock");
        then(productRepository).should().save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: non-existent category throws ResourceNotFoundException")
    void createProduct_categoryNotFound() {
        CreateProductRequest request = new CreateProductRequest(
                "Gadget", "", new BigDecimal("49.99"), 10, 999L);

        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(request, "sellerUser"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");

        then(productRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("updateProduct: seller who does not own product throws AccessDeniedException")
    void updateProduct_notOwner() {
        User owner = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        User otherSeller = user("otherSeller", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product existing = product("Laptop", "desc", DEFAULT_PRICE, 5,
                ProductStatus.ACTIVE, category, owner);

        given(productRepository.findById(100L)).willReturn(Optional.of(existing));
        given(userRepository.findByUsername("otherSeller")).willReturn(Optional.of(otherSeller));

        UpdateProductRequest request = new UpdateProductRequest(
                "Hacked Name", "desc", DEFAULT_PRICE, 5, ProductStatus.ACTIVE, null);

        assertThatThrownBy(() -> productService.updateProduct(100L, request, "otherSeller"))
                .isInstanceOf(AccessDeniedException.class);

        then(productRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("deleteProduct: admin can delete any seller's product")
    void deleteProduct_asAdmin() {
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        User admin = user("adminUser", Set.of(new Role("ROLE_ADMIN")));
        Category category = category("Electronics");
        Product existing = product("Laptop", "desc", DEFAULT_PRICE, 5,
                ProductStatus.ACTIVE, category, seller);

        given(productRepository.findById(100L)).willReturn(Optional.of(existing));
        given(userRepository.findByUsername("adminUser")).willReturn(Optional.of(admin));

        productService.deleteProduct(100L, "adminUser");

        then(productRepository).should().delete(existing);
    }

    @Test
    @DisplayName("getProductById: missing product throws ResourceNotFoundException")
    void getProductById_notFound() {
        given(productRepository.findById(9999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(9999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
    }

    @Test
    @DisplayName("computeStockStatus: quantity > 0 returns In Stock")
    void computeStockStatus_inStock() {
        assertThat(productService.computeStockStatus(5, ProductStatus.ACTIVE))
                .isEqualTo("In Stock");
    }

    @Test
    @DisplayName("computeStockStatus: quantity=0 and ACTIVE returns Pre Order")
    void computeStockStatus_preOrder() {
        assertThat(productService.computeStockStatus(0, ProductStatus.ACTIVE))
                .isEqualTo("Pre Order");
    }

    @Test
    @DisplayName("computeStockStatus: quantity=0 and DISCONTINUED returns Out of Stock")
    void computeStockStatus_outOfStock() {
        assertThat(productService.computeStockStatus(0, ProductStatus.DISCONTINUED))
                .isEqualTo("Out of Stock");
    }

    @Test
    @DisplayName("getProductsBySeller: returns mapped product list")
    void getProductsBySeller_returnsMappedList() {
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product product = product("Laptop", "desc", DEFAULT_PRICE, 5,
                ProductStatus.ACTIVE, category, seller);

        given(userRepository.findByUsername("sellerUser")).willReturn(Optional.of(seller));
        given(productRepository.findBySeller(seller)).willReturn(List.of(product));

        List<ProductResponse> responses = productService.getProductsBySeller("sellerUser");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Laptop");
        assertThat(responses.get(0).stockStatus()).isEqualTo("In Stock");
    }

    private User user(String username, Set<Role> roles) {
        User user = new User(username, username + "@stockmate.test", "hashed");
        user.setRoles(roles);
        return user;
    }

    private Category category(String name) {
        return new Category(name, name + " category");
    }

    private Product product(String name,
            String description,
            BigDecimal price,
            int stockQuantity,
            ProductStatus status,
            Category category,
            User seller) {
        Product product = new Product(name, description, price, stockQuantity, category, seller);
        product.setStatus(status);
        return product;
    }
}
