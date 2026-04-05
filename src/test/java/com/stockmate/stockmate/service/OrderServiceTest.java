package com.stockmate.stockmate.service;

import com.stockmate.stockmate.dto.request.PlaceOrderRequest;
import com.stockmate.stockmate.dto.request.UpdateOrderStatusRequest;
import com.stockmate.stockmate.dto.response.OrderResponse;
import com.stockmate.stockmate.exception.InsufficientStockException;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Category;
import com.stockmate.stockmate.model.Order;
import com.stockmate.stockmate.model.Product;
import com.stockmate.stockmate.model.Role;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.model.enums.OrderStatus;
import com.stockmate.stockmate.model.enums.ProductStatus;
import com.stockmate.stockmate.repository.OrderRepository;
import com.stockmate.stockmate.repository.ProductRepository;
import com.stockmate.stockmate.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for OrderServiceImpl.
 *
 * Order placement and cancellation carry the most complex business invariants
 * in StockMate (stock deduction, ownership guards, status-machine transitions).
 * These tests validate behavior against the current record DTO contracts.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("999.99");

    @Test
    @DisplayName("placeOrder: valid request decrements stock and returns OrderResponse")
    void placeOrder_success() {
        User buyer = user("buyerUser", Set.of(new Role("ROLE_BUYER")));
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product product = product("Laptop", DEFAULT_PRICE, 5, ProductStatus.ACTIVE, category, seller);
        PlaceOrderRequest request = new PlaceOrderRequest(10L, 2);
        Order savedOrder = new Order(buyer, product, 2, new BigDecimal("1999.98"));

        given(userRepository.findByUsername("buyerUser")).willReturn(Optional.of(buyer));
        given(productRepository.findById(10L)).willReturn(Optional.of(product));
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        OrderResponse response = orderService.placeOrder(request, "buyerUser");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(product.getStockQuantity()).isEqualTo(3);
        then(productRepository).should().save(product);
        then(orderRepository).should().save(any(Order.class));
    }

    @Test
    @DisplayName("placeOrder: quantity > stock throws InsufficientStockException")
    void placeOrder_insufficientStock() {
        User buyer = user("buyerUser", Set.of(new Role("ROLE_BUYER")));
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product product = product("Laptop", DEFAULT_PRICE, 5, ProductStatus.ACTIVE, category, seller);
        PlaceOrderRequest request = new PlaceOrderRequest(10L, 10);

        given(userRepository.findByUsername("buyerUser")).willReturn(Optional.of(buyer));
        given(productRepository.findById(10L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.placeOrder(request, "buyerUser"))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(product.getStockQuantity()).isEqualTo(5);
        then(productRepository).should(never()).save(any());
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("placeOrder: non-existent product throws ResourceNotFoundException")
    void placeOrder_productNotFound() {
        User buyer = user("buyerUser", Set.of(new Role("ROLE_BUYER")));
        PlaceOrderRequest request = new PlaceOrderRequest(9999L, 1);

        given(userRepository.findByUsername("buyerUser")).willReturn(Optional.of(buyer));
        given(productRepository.findById(9999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(request, "buyerUser"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");

        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("getOrdersByBuyer: returns only orders belonging to calling buyer")
    void getOrdersByBuyer() {
        User buyer = user("buyerUser", Set.of(new Role("ROLE_BUYER")));
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product product = product("Laptop", DEFAULT_PRICE, 5, ProductStatus.ACTIVE, category, seller);
        Order pendingOrder = new Order(buyer, product, 2, new BigDecimal("1999.98"));

        given(userRepository.findByUsername("buyerUser")).willReturn(Optional.of(buyer));
        given(orderRepository.findByBuyer(buyer)).willReturn(List.of(pendingOrder));

        List<OrderResponse> responses = orderService.getOrdersByBuyer("buyerUser");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo("PENDING");
        assertThat(responses.get(0).productName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("updateOrderStatus: seller who owns the product can advance order status")
    void updateOrderStatus_asSellerOwner() {
        User buyer = user("buyerUser", Set.of(new Role("ROLE_BUYER")));
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product product = product("Laptop", DEFAULT_PRICE, 5, ProductStatus.ACTIVE, category, seller);
        Order pendingOrder = new Order(buyer, product, 2, new BigDecimal("1999.98"));
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);

        given(orderRepository.findById(100L)).willReturn(Optional.of(pendingOrder));
        given(userRepository.findByUsername("sellerUser")).willReturn(Optional.of(seller));
        given(orderRepository.save(any(Order.class))).willReturn(pendingOrder);

        OrderResponse response = orderService.updateOrderStatus(100L, request, "sellerUser");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CONFIRMED");
        then(orderRepository).should().save(any(Order.class));
    }

    @Test
    @DisplayName("cancelOrder: buyer cancels own order and stock is restored")
    void cancelOrder_success() {
        User buyer = user("buyerUser", Set.of(new Role("ROLE_BUYER")));
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product product = product("Laptop", DEFAULT_PRICE, 3, ProductStatus.ACTIVE, category, seller);
        Order pendingOrder = new Order(buyer, product, 2, new BigDecimal("1999.98"));

        given(userRepository.findByUsername("buyerUser")).willReturn(Optional.of(buyer));
        given(orderRepository.findByIdAndBuyer(100L, buyer)).willReturn(Optional.of(pendingOrder));
        given(orderRepository.save(any(Order.class))).willReturn(pendingOrder);

        OrderResponse cancelled = orderService.cancelOrder(100L, "buyerUser");

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(product.getStockQuantity()).isEqualTo(5);
        then(productRepository).should().save(product);
    }

    @Test
    @DisplayName("cancelOrder: buyer who does not own order throws AccessDeniedException")
    void cancelOrder_notOwner() {
        User otherBuyer = user("otherBuyer", Set.of(new Role("ROLE_BUYER")));

        given(userRepository.findByUsername("otherBuyer")).willReturn(Optional.of(otherBuyer));
        given(orderRepository.findByIdAndBuyer(100L, otherBuyer)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(100L, "otherBuyer"))
                .isInstanceOf(AccessDeniedException.class);

        then(productRepository).should(never()).save(any());
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder: cancelling an already-cancelled order throws IllegalStateException")
    void cancelOrder_alreadyCancelled() {
        User buyer = user("buyerUser", Set.of(new Role("ROLE_BUYER")));
        User seller = user("sellerUser", Set.of(new Role("ROLE_SELLER")));
        Category category = category("Electronics");
        Product product = product("Laptop", DEFAULT_PRICE, 3, ProductStatus.ACTIVE, category, seller);
        Order pendingOrder = new Order(buyer, product, 2, new BigDecimal("1999.98"));
        pendingOrder.setStatus(OrderStatus.CANCELLED);

        given(userRepository.findByUsername("buyerUser")).willReturn(Optional.of(buyer));
        given(orderRepository.findByIdAndBuyer(100L, buyer)).willReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(100L, "buyerUser"))
                .isInstanceOf(IllegalStateException.class);

        then(productRepository).should(never()).save(any());
        then(orderRepository).should(never()).save(any());
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
            BigDecimal price,
            int stockQuantity,
            ProductStatus status,
            Category category,
            User seller) {
        Product product = new Product(name, "description", price, stockQuantity, category, seller);
        product.setStatus(status);
        return product;
    }
}
