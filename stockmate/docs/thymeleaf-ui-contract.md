# StockMate Thymeleaf UI Contract

This document describes the model attributes and DTO form bindings used by the Thymeleaf UI templates.

## 1) Page to Model/DTO Mapping

| Page | Template | Required Model Attributes | Form DTO Binding |
|---|---|---|---|
| `/auth/login` | `auth/login` | `loginRequest`, optional `errorMessage`, `logoutMessage` | `LoginRequest(username, password)` |
| `/auth/register` | `auth/register` | `registerRequest`, optional `errorMessage` | `RegisterRequest(username, email, password, role)` |
| `/products` | `products/catalogue` | `products`, `categories`, `currentPage`, `totalPages`, `totalItems`, `keyword`, `selectedCat`, `sort` | none (GET filter form only) |
| `/products/{id}` | `products/detail` | `product`, `placeOrderRequest`, optional `successMessage`, `errorMessage` | `PlaceOrderRequest(productId, quantity)` |
| `/products/new` | `products/form` | `productRequest`, `categories`, `formAction=create` | `CreateProductRequest(name, description, price, stockQuantity, categoryId)` |
| `/products/{id}/edit` | `products/form` | `product`, `productRequest`, `categories`, `formAction=edit` | `UpdateProductRequest(name, description, price, stockQuantity, status, categoryId)` |
| `/orders/my` | `orders/buyer-orders` | `orders` | Cancel action form (no DTO body) |
| `/orders/seller` | `orders/seller-orders` | `orders`, `updateStatusRequest` | `UpdateOrderStatusRequest(newStatus)` |
| `/admin` | `admin/dashboard` | `totalUsers`, `totalOrders`, `recentOrders` | none |
| `/admin/users` | `admin/users` | `users`, `roleChangeRequest` | `ChangeRoleRequest(newRole)` for role form |
| `/categories` | `admin/categories` | `categories`, `categoryRequest` | `CreateCategoryRequest(name, description)` |
| `/dashboard/seller` | `dashboard/seller` | `totalProducts`, `pendingOrders`, `products`, `recentOrders` | none |
| `/dashboard/buyer` | `dashboard/buyer` | `totalOrders`, `activeOrders`, `recentOrders` | none |
| 403 error | `error/403` | optional `errorTitle`, `errorMessage` | none |

## 2) DTO Fields Used in Forms

### LoginRequest
- `username`
- `password`

### RegisterRequest
- `username`
- `email`
- `password`
- `role` (`ROLE_BUYER` or `ROLE_SELLER`)

### CreateProductRequest
- `name`
- `description`
- `price`
- `stockQuantity`
- `categoryId`

### UpdateProductRequest
- `name`
- `description`
- `price`
- `stockQuantity`
- `status` (`ACTIVE` or `DISCONTINUED`)
- `categoryId`

### PlaceOrderRequest
- `productId`
- `quantity`

### UpdateOrderStatusRequest
- `newStatus` (`CONFIRMED`, `SHIPPED`, `DELIVERED` in seller/admin views)

### CreateCategoryRequest
- `name`
- `description`

### ChangeRoleRequest
- `newRole` (`ROLE_BUYER`, `ROLE_SELLER`, `ROLE_ADMIN`)

## 3) Example Controller Methods Per Requested Page

```java
// /auth/login
@GetMapping("/auth/login")
public String showLoginForm(Model model,
                            @RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout) {
    model.addAttribute("loginRequest", new LoginRequest("", ""));
    if (error != null) model.addAttribute("errorMessage", "Invalid username or password.");
    if (logout != null) model.addAttribute("logoutMessage", "You have been logged out successfully.");
    return "auth/login";
}

// /auth/register
@GetMapping("/auth/register")
public String showRegisterForm(Model model) {
    model.addAttribute("registerRequest", new RegisterRequest("", "", "", "ROLE_BUYER"));
    return "auth/register";
}

// /products
@GetMapping("/products")
public String listProducts(@RequestParam(required = false) String keyword,
                           @RequestParam(required = false) Long category,
                           @RequestParam(defaultValue = "newest") String sort,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "12") int size,
                           Model model) {
    // service calls omitted for brevity
    model.addAttribute("products", products);
    model.addAttribute("categories", categories);
    model.addAttribute("currentPage", page);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("totalItems", totalItems);
    model.addAttribute("keyword", keyword);
    model.addAttribute("selectedCat", category);
    model.addAttribute("sort", sort);
    return "products/catalogue";
}

// /products/{id}
@GetMapping("/products/{id}")
public String productDetail(@PathVariable Long id, Model model) {
    ProductResponse product = productService.getProductById(id);
    model.addAttribute("product", product);
    model.addAttribute("placeOrderRequest", new PlaceOrderRequest(product.id(), 1));
    return "products/detail";
}

// /products/new
@GetMapping("/products/new")
public String showCreateForm(Model model) {
    model.addAttribute("productRequest", new CreateProductRequest("", "", null, 0, null));
    model.addAttribute("categories", categoryService.getAllCategories());
    model.addAttribute("formAction", "create");
    return "products/form";
}

// /products/{id}/edit
@GetMapping("/products/{id}/edit")
public String showEditForm(@PathVariable Long id, Model model) {
    ProductResponse product = productService.getProductById(id);
    model.addAttribute("product", product);
    model.addAttribute("productRequest", new UpdateProductRequest(
            product.name(), product.description(), product.price(),
            product.stockQuantity(), ProductStatus.valueOf(product.status()), product.categoryId()));
    model.addAttribute("categories", categoryService.getAllCategories());
    model.addAttribute("formAction", "edit");
    return "products/form";
}

// /orders/my
@GetMapping("/orders/my")
public String buyerOrders(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
    model.addAttribute("orders", orderService.getOrdersByBuyer(currentUser.getUsername()));
    return "orders/buyer-orders";
}

// /orders/seller
@GetMapping("/orders/seller")
public String sellerOrders(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
    model.addAttribute("orders", orderService.getOrdersBySeller(currentUser.getUsername()));
    model.addAttribute("updateStatusRequest", new UpdateOrderStatusRequest(null));
    return "orders/seller-orders";
}

// /admin
@GetMapping("/admin")
public String adminDashboard(Model model) {
    model.addAttribute("totalUsers", userService.getAllUsers().size());
    List<OrderResponse> orders = orderService.getAllOrders();
    model.addAttribute("totalOrders", orders.size());
    model.addAttribute("recentOrders", orders.stream().limit(10).toList());
    return "admin/dashboard";
}

// /admin/users
@GetMapping("/admin/users")
public String listUsers(Model model) {
    model.addAttribute("users", userService.getAllUsers());
    model.addAttribute("roleChangeRequest", new ChangeRoleRequest("ROLE_BUYER"));
    return "admin/users";
}

// /categories
@GetMapping("/categories")
public String listCategories(Model model) {
    model.addAttribute("categories", categoryService.getAllCategories());
    model.addAttribute("categoryRequest", new CreateCategoryRequest("", ""));
    return "admin/categories";
}

// /dashboard/seller
@GetMapping("/dashboard/seller")
public String sellerDashboard(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
    List<ProductResponse> products = productService.getProductsBySeller(currentUser.getUsername());
    List<OrderResponse> orders = orderService.getOrdersBySeller(currentUser.getUsername());
    model.addAttribute("totalProducts", products.size());
    model.addAttribute("pendingOrders", orders.stream().filter(o -> "PENDING".equals(o.status())).count());
    model.addAttribute("products", products.stream().limit(5).toList());
    model.addAttribute("recentOrders", orders.stream().limit(5).toList());
    return "dashboard/seller";
}

// /dashboard/buyer
@GetMapping("/dashboard/buyer")
public String buyerDashboard(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
    List<OrderResponse> orders = orderService.getOrdersByBuyer(currentUser.getUsername());
    model.addAttribute("totalOrders", orders.size());
    model.addAttribute("activeOrders", orders.stream()
            .filter(o -> "PENDING".equals(o.status()) || "CONFIRMED".equals(o.status()) || "SHIPPED".equals(o.status()))
            .count());
    model.addAttribute("recentOrders", orders.stream().limit(5).toList());
    return "dashboard/buyer";
}
```

## 4) Fragment Usage Pattern

All pages include:
- `fragments/header :: siteHeader(...)`
- `fragments/header :: flashMessages`
- `fragments/footer :: siteFooter`

This keeps navigation, flash messages, and footer consistent across all views.
