package com.stockmate.stockmate.service;

import com.stockmate.stockmate.dto.request.PlaceOrderRequest;
import com.stockmate.stockmate.dto.request.UpdateOrderStatusRequest;
import com.stockmate.stockmate.dto.response.OrderResponse;
import com.stockmate.stockmate.exception.InsufficientStockException;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Order;
import com.stockmate.stockmate.model.Product;
import com.stockmate.stockmate.model.User;
import com.stockmate.stockmate.model.enums.OrderStatus;
import com.stockmate.stockmate.repository.OrderRepository;
import com.stockmate.stockmate.repository.ProductRepository;
import com.stockmate.stockmate.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OrderServiceImpl — order lifecycle and stock-adjustment rules.
 *
 * Dependencies (constructor-injected, final):
 *   OrderRepository   — order persistence and queries
 *   ProductRepository — stock read/write (decrement on place, increment on cancel)
 *   UserRepository    — load buyer/seller entities for queries and ownership checks
 *
 * Coupling check:
 *   ✅ No reference to ProductService (avoids circular dep)
 *      Stock is adjusted directly via ProductRepository + entity helpers.
 *   ✅ No reference to UserService (uses UserRepository for entity access only)
 *   ✅ Stock adjustment and order persist happen in the same @Transactional boundary.
 */
@Slf4j
@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository   orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository    userRepository;

    // ── Constructor injection ─────────────────────────────────

    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductRepository productRepository,
                            UserRepository userRepository) {
        this.orderRepository   = orderRepository;
        this.productRepository = productRepository;
        this.userRepository    = userRepository;
    }

    // ── BUYER: place order ────────────────────────────────────

    /**
     * placeOrder
     *
     * Inputs     : PlaceOrderRequest { productId, quantity }, buyerUsername
     * Validation : product must exist · stock must cover requested qty
     *              · @PreAuthorize BUYER
     * Repos      : UserRepository + ProductRepository + OrderRepository.save()
     * Business   : validate stock → decrement stock → snapshot totalPrice → persist PENDING
     *              All in one transaction — partial failure rolls back.
     * Output     : OrderResponse
     */
    @Override
    @PreAuthorize("hasRole('BUYER')")
    public OrderResponse placeOrder(PlaceOrderRequest request, String buyerUsername) {
        log.info("Buyer '{}' placing order: productId={}, qty={}",
                buyerUsername, request.productId(), request.quantity());

        User    buyer   = findUserOrThrow(buyerUsername);
        Product product = findProductOrThrow(request.productId());

        // ── Stock validation (FR-ORD-02) ──────────────────────
        if (product.getStockQuantity() < request.quantity()) {
            throw new InsufficientStockException(
                    "Insufficient stock for product '" + product.getName() +
                            "'. Available: " + product.getStockQuantity() +
                            ", requested: " + request.quantity());
        }

        // ── Stock decrement (FR-ORD-03) ───────────────────────
        product.decrementStock(request.quantity());
        productRepository.save(product);

        // ── Snapshot total price ──────────────────────────────
        BigDecimal totalPrice = product.getPrice()
                .multiply(BigDecimal.valueOf(request.quantity()));

        // ── Persist order ─────────────────────────────────────
        Order order = new Order(buyer, product, request.quantity(), totalPrice);
        Order saved = orderRepository.save(order);

        log.info("Order placed: id={}, buyer={}, product={}, qty={}, total={}",
                saved.getId(), buyerUsername, product.getName(),
                request.quantity(), totalPrice);

        return toResponse(saved);
    }

    // ── BUYER: cancel order ───────────────────────────────────

    /**
     * cancelOrder
     *
     * Inputs     : orderId, buyerUsername
     * Validation : order must exist AND belong to this buyer (ownership FR-AUTH-09)
     *              · order must not already be DELIVERED or CANCELLED (terminal states)
     *              · @PreAuthorize BUYER
     * Repos      : OrderRepository + ProductRepository (stock increment)
     * Business   : set status → CANCELLED · restore stock (FR-ORD-01)
     * Output     : updated OrderResponse
     */
    @Override
    @PreAuthorize("hasRole('BUYER')")
    public OrderResponse cancelOrder(Long orderId, String buyerUsername) {
        log.info("Buyer '{}' cancelling order id={}", buyerUsername, orderId);

        User  buyer = findUserOrThrow(buyerUsername);
        Order order = orderRepository.findByIdAndBuyer(orderId, buyer)
                .orElseThrow(() -> {
                    log.warn("Cancel denied: orderId={} not found or not owned by '{}'",
                            orderId, buyerUsername);
                    // Use AccessDeniedException rather than ResourceNotFoundException:
                    // we don't want to reveal that the order exists for another buyer.
                    return new AccessDeniedException(
                            "Order not found or does not belong to you.");
                });

        // ── Terminal state guard ───────────────────────────────
        if (order.getStatus() == OrderStatus.DELIVERED ||
                order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot cancel an order with status: " + order.getStatus());
        }

        // ── Restore stock (FR-ORD-01) ─────────────────────────
        Product product = order.getProduct();
        product.incrementStock(order.getQuantity());
        productRepository.save(product);

        // ── Update status ─────────────────────────────────────
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        log.info("Order cancelled: id={}, stock restored for product id={}",
                saved.getId(), product.getId());

        return toResponse(saved);
    }

    // ── BUYER: view own orders ────────────────────────────────

    /**
     * getOrdersByBuyer
     *
     * Inputs     : buyerUsername
     * Validation : @PreAuthorize BUYER · user must exist
     * Repos      : UserRepository + OrderRepository.findByBuyer()
     * Business   : none — map to DTO
     * Output     : List<OrderResponse>
     */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('BUYER')")
    public List<OrderResponse> getOrdersByBuyer(String buyerUsername) {
        log.debug("Fetching orders for buyer '{}'", buyerUsername);
        User buyer = findUserOrThrow(buyerUsername);
        return orderRepository.findByBuyer(buyer)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── SELLER: view orders for own products ──────────────────

    /**
     * getOrdersBySeller
     *
     * Inputs     : sellerUsername
     * Validation : @PreAuthorize SELLER · user must exist
     * Repos      : UserRepository + OrderRepository.findByProductSellerId()
     * Business   : none — map to DTO
     * Output     : List<OrderResponse>
     */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SELLER')")
    public List<OrderResponse> getOrdersBySeller(String sellerUsername) {
        log.debug("Fetching orders for seller '{}'", sellerUsername);
        User seller = findUserOrThrow(sellerUsername);
        return orderRepository.findByProductSellerId(seller.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── SELLER / ADMIN: update order status ───────────────────

    /**
     * updateOrderStatus
     *
     * Inputs     : orderId, UpdateOrderStatusRequest { newStatus }, currentUsername
     * Validation : order must exist · SELLER ownership of the product · terminal state guard
     *              · @PreAuthorize SELLER or ADMIN
     * Repos      : OrderRepository + UserRepository
     * Business   : SELLER may only update orders for their own product;
     *              ADMIN may update any; DELIVERED and CANCELLED are terminal.
     * Output     : updated OrderResponse
     */
    @Override
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request,
                                           String currentUsername) {
        log.info("User '{}' updating order id={} → status={}",
                currentUsername, orderId, request.newStatus());

        Order order       = findOrderOrThrow(orderId);
        User  currentUser = findUserOrThrow(currentUsername);

        // ── Terminal state guard ───────────────────────────────
        if (order.getStatus() == OrderStatus.DELIVERED ||
                order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot update a terminal order (status: " + order.getStatus() + ").");
        }

        // ── Ownership check for SELLER ─────────────────────────
        boolean isAdmin = currentUser.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            // SELLER path: the order's product must belong to this seller
            boolean ownsSellersProduct = order.getProduct()
                    .getSeller().getUsername().equals(currentUsername);
            if (!ownsSellersProduct) {
                log.warn("Seller '{}' tried to update order id={} for another seller's product",
                        currentUsername, orderId);
                throw new AccessDeniedException(
                        "You may only update orders for your own products.");
            }
        }

        order.setStatus(request.newStatus());
        Order saved = orderRepository.save(order);

        log.info("Order status updated: id={} → {}", saved.getId(), request.newStatus());
        return toResponse(saved);
    }

    // ── ADMIN: all orders ─────────────────────────────────────

    /**
     * getAllOrders
     *
     * Inputs     : none
     * Validation : @PreAuthorize ADMIN
     * Repos      : OrderRepository.findAllWithDetails()
     * Business   : none — map to DTO
     * Output     : List<OrderResponse>
     */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderResponse> getAllOrders() {
        log.debug("Admin: fetching all orders");
        return orderRepository.findAllWithDetails()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + id));
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + id));
    }

    /**
     * Entity → DTO mapping.
     * Extracts all fields needed by the UI without leaking entities.
     * buyer and product are JOIN FETCHed by the repository queries,
     * so no lazy-loading surprises here.
     */
    private OrderResponse toResponse(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getBuyer().getId(),
                o.getBuyer().getUsername(),
                o.getProduct().getId(),
                o.getProduct().getName(),
                o.getProduct().getSeller().getUsername(),
                o.getQuantity(),
                o.getTotalPrice(),
                o.getStatus().name(),
                o.getOrderedAt()
        );
    }
}
