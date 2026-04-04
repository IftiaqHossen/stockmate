# CLAUDE.md — StockMate Project Context

> This file exists to give Claude Code (and any developer) immediate, accurate context about
> the project before writing a single line of code. Read it fully before starting a session.

---

## Project Overview

**StockMate** is a role-based inventory management web application built as a full-stack
academic project. The goal is not a commercial product — it is to demonstrate a professional
software development workflow: secure authentication, clean layered architecture, database
design, containerisation, CI/CD, and cloud deployment.

Three user roles interact with the system in controlled, role-appropriate ways:
- **ADMIN** — full platform control: manages users, categories, all products and orders
- **SELLER** — manages their own product listings and views orders placed against them
- **BUYER** — browses the product catalogue, places orders, cancels orders

**Live deployment target:** Render (publicly accessible URL required for submission)

---

## Architecture

StockMate follows a **strict 3-layer architecture**. Never mix responsibilities across layers.
A change to one layer must not require changes in the other two.

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────┐
│  Presentation Layer  (controller/)                  │
│  • Maps URLs to service calls                       │
│  • Returns Thymeleaf views OR JSON                  │
│  • No business logic here — ever                    │
└───────────────────┬─────────────────────────────────┘
                    │ calls (via DTOs only)
                    ▼
┌─────────────────────────────────────────────────────┐
│  Business Logic Layer  (service/)                   │
│  • All business rules live here                     │
│  • @PreAuthorize method-level security              │
│  • 100% unit-testable (injected via interface)      │
└───────────────────┬─────────────────────────────────┘
                    │ calls
                    ▼
┌─────────────────────────────────────────────────────┐
│  Data Access Layer  (repository/)                   │
│  • Spring Data JPA interfaces only                  │
│  • No business logic here — ever                    │
└───────────────────┬─────────────────────────────────┘
                    │
                    ▼
              PostgreSQL 15+
```

**Supporting packages:**

| Package | Purpose |
|---|---|
| `model/` (entity/) | JPA entity classes mapped to DB tables |
| `dto/` | Request and response DTOs — entities NEVER cross the API boundary |
| `security/` + `config/` | Spring Security config, UserDetailsService, session management |
| `exception/` | Custom exception classes + global `@ControllerAdvice` handler |

---

## Tech Stack

| Component | Technology | Version |
|---|---|---|
| Language | Java | 17 (LTS) |
| Framework | Spring Boot | 3.x |
| Web Layer | Spring MVC + Thymeleaf | Latest |
| Security | Spring Security 6 | Latest |
| ORM | Spring Data JPA / Hibernate | Latest |
| Database | PostgreSQL | 15+ |
| Build Tool | Maven | 3.9+ |
| Testing | JUnit 5 + Mockito + MockMvc | Latest |
| Containerisation | Docker + Docker Compose | Latest |
| CI/CD | GitHub Actions | — |
| Hosting | Render | — |

**The stack is fixed by the course specification. Do not introduce alternatives.**

---

## Coding Conventions

### General
- Java 17 — use records for DTOs where appropriate, but not for JPA entities
- All field injection is via constructor injection (not `@Autowired` on fields)
- `final` fields on all injected dependencies
- No `System.out.println` — use SLF4J (`@Slf4j`) for logging
- No hardcoded credentials anywhere in source code — use environment variables

### Naming
- Classes: `PascalCase` — e.g. `ProductService`, `OrderController`, `UserDto`
- Methods/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Database columns: `snake_case` (configured via `spring.jpa.hibernate.naming.physical-strategy`)
- Package names: all lowercase, no underscores

### DTO Rules
- Every API request uses a `*Request` DTO (e.g. `CreateProductRequest`)
- Every API response uses a `*Response` DTO (e.g. `ProductResponse`)
- JPA entities are **never** passed to or returned from controllers
- DTOs are validated with Bean Validation annotations (`@NotBlank`, `@Min`, etc.)

### Security Rules
- URL-level restrictions enforced in `SecurityConfig` (e.g. `/admin/**` → ADMIN only)
- Method-level restrictions enforced with `@PreAuthorize` in service layer
- Sensitive operations (e.g. a seller editing another seller's product) must use method-level
  ownership checks — role-only is not sufficient
- `AccessDeniedException` is thrown on forbidden method access; handled by `GlobalExceptionHandler`

### Exception Handling
All exceptions are handled centrally in `GlobalExceptionHandler` (`@ControllerAdvice`):

| Exception | HTTP Status |
|---|---|
| `ResourceNotFoundException` | 404 Not Found |
| `AccessDeniedException` | 403 Forbidden |
| `MethodArgumentNotValidException` | 400 Bad Request (with field-level messages) |
| Generic `Exception` | 500 Internal Server Error |

Never catch and swallow exceptions silently in service or controller code.

### Stock Status (computed, never stored)
Stock status is **derived at the service layer** from two stored fields. Never add a
`stock_status` column to the database.

| Displayed Status | Condition |
|---|---|
| In Stock | `stock_quantity > 0` (any product status) |
| Pre Order | `stock_quantity = 0` AND `product.status = ACTIVE` |
| Out of Stock | `stock_quantity = 0` AND `product.status = DISCONTINUED` |

---

## Folder Structure

```
stockmate/
├── .claude/
│   └── settings.json                  # Team-wide Claude Code behaviour settings
├── .github/
│   └── workflows/
│       ├── build-and-test.yml         # Runs on push to feature/*, UAT, PROD, main + PRs
│       ├── deploy-uat.yml             # Triggers Render UAT deploy after build passes
│       ├── deploy-prod.yml            # Triggers Render PROD deploy after build passes
│       └── deploy-main.yml            # Triggers Render production deploy after build passes
├── src/
│   ├── main/
│   │   ├── java/com/stockmate/
│   │   │   ├── StockmateApplication.java        # Spring Boot entry point (@SpringBootApplication)
│   │   │   ├── controller/                      # PRESENTATION LAYER — HTTP only, no business logic
│   │   │   │   ├── AuthController.java          # /auth/** — PUBLIC (register, login, logout)
│   │   │   │   ├── ProductController.java       # /products/** — all authenticated roles
│   │   │   │   ├── OrderController.java         # /orders/** — BUYER, SELLER, ADMIN
│   │   │   │   ├── CategoryController.java      # /categories/** — ADMIN write, ALL read
│   │   │   │   └── AdminController.java         # /admin/** — ADMIN only
│   │   │   ├── service/                         # BUSINESS LOGIC LAYER — all rules here
│   │   │   │   ├── UserService.java             # Interface
│   │   │   │   ├── UserServiceImpl.java         # Implementation
│   │   │   │   ├── ProductService.java          # Interface
│   │   │   │   ├── ProductServiceImpl.java      # Implementation (includes stock status logic)
│   │   │   │   ├── OrderService.java            # Interface
│   │   │   │   ├── OrderServiceImpl.java        # Implementation (place, cancel, status update)
│   │   │   │   ├── CategoryService.java         # Interface
│   │   │   │   └── CategoryServiceImpl.java     # Implementation
│   │   │   ├── repository/                      # DATA ACCESS LAYER — JPA interfaces only
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── ProductRepository.java
│   │   │   │   ├── OrderRepository.java
│   │   │   │   └── CategoryRepository.java
│   │   │   ├── model/                           # JPA entity classes (mapped to DB tables)
│   │   │   │   ├── User.java                    # users table
│   │   │   │   ├── Role.java                    # roles table
│   │   │   │   ├── Product.java                 # products table
│   │   │   │   ├── Order.java                   # orders table
│   │   │   │   ├── Category.java                # categories table
│   │   │   │   └── enums/
│   │   │   │       ├── OrderStatus.java         # PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
│   │   │   │       └── ProductStatus.java       # ACTIVE, DISCONTINUED
│   │   │   ├── dto/                             # Request & response DTOs — no entity leakage
│   │   │   │   ├── request/
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── CreateProductRequest.java
│   │   │   │   │   ├── UpdateProductRequest.java
│   │   │   │   │   ├── PlaceOrderRequest.java
│   │   │   │   │   ├── UpdateOrderStatusRequest.java
│   │   │   │   │   └── CreateCategoryRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── UserResponse.java
│   │   │   │       ├── ProductResponse.java     # Includes computed stockStatus field
│   │   │   │       ├── OrderResponse.java
│   │   │   │       └── CategoryResponse.java
│   │   │   ├── security/                        # Spring Security configuration
│   │   │   │   ├── SecurityConfig.java          # URL-based access rules, CSRF, session config
│   │   │   │   ├── CustomUserDetailsService.java # Loads user by username for Spring Security
│   │   │   │   └── CustomUserDetails.java       # UserDetails wrapper around User entity
│   │   │   ├── exception/                       # Custom exceptions + global handler
│   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   ├── InsufficientStockException.java
│   │   │   │   └── GlobalExceptionHandler.java  # @ControllerAdvice — all exceptions handled here
│   │   │   └── config/                          # Non-security Spring config (if needed)
│   │   │       └── AppConfig.java
│   │   └── resources/
│   │       ├── application.properties           # Base config — no credentials here
│   │       ├── application-dev.properties       # Dev overrides (local DB, debug logging)
│   │       ├── application-prod.properties      # Prod overrides (references env vars)
│   │       ├── templates/                       # Thymeleaf HTML templates
│   │       │   ├── auth/
│   │       │   │   ├── login.html               # /auth/login — PUBLIC
│   │       │   │   └── register.html            # /auth/register — PUBLIC
│   │       │   ├── products/
│   │       │   │   ├── catalogue.html           # /products — all authenticated
│   │       │   │   ├── detail.html              # /products/{id}
│   │       │   │   └── form.html                # /products/new and /products/{id}/edit — SELLER
│   │       │   ├── orders/
│   │       │   │   ├── buyer-orders.html        # /orders/my — BUYER
│   │       │   │   └── seller-orders.html       # /orders/seller — SELLER
│   │       │   ├── admin/
│   │       │   │   ├── dashboard.html           # /admin — ADMIN
│   │       │   │   ├── users.html               # /admin/users — ADMIN
│   │       │   │   └── categories.html          # /categories — ADMIN
│   │       │   ├── dashboard/
│   │       │   │   ├── seller.html              # /dashboard/seller — SELLER
│   │       │   │   └── buyer.html               # /dashboard/buyer — BUYER
│   │       │   ├── error/
│   │       │   │   └── 403.html                 # Forbidden page
│   │       │   └── fragments/
│   │       │       ├── header.html              # Shared nav bar fragment
│   │       │       └── footer.html              # Shared footer fragment
│   │       └── static/
│   │           ├── css/
│   │           │   └── main.css
│   │           └── js/
│   │               └── main.js
│   └── test/
│       └── java/com/stockmate/
│           ├── service/                         # Unit tests — JUnit 5 + Mockito (min. 15 tests)
│           │   ├── ProductServiceTest.java      # 8 test cases (see Testing section)
│           │   ├── OrderServiceTest.java        # 7 test cases (see Testing section)
│           │   ├── UserServiceTest.java         # 5 test cases (see Testing section)
│           │   └── CategoryServiceTest.java     # 3 test cases (see Testing section)
│           └── controller/                      # Integration tests — SpringBootTest + MockMvc (min. 3)
│               ├── AuthControllerTest.java      # Register + login flows
│               ├── ProductControllerTest.java   # Product CRUD endpoints
│               └── OrderControllerTest.java     # Order placement and cancellation
├── docker/
│   └── init.sql                                 # Optional: DB seed data for local dev
├── docs/
│   ├── architecture-diagram.png                 # Required for README
│   └── er-diagram.png                           # Required for README
├── Dockerfile                                   # Multi-stage: Maven build → eclipse-temurin:17-jre-alpine
├── docker-compose.yml                           # app + postgres:15-alpine services
├── .env.example                                 # Template for all required env vars (no real values)
├── .gitignore
├── pom.xml                                      # Maven build file
├── README.md                                    # Full project docs (see Documentation section)
└── CLAUDE.md                                    # This file
```

---

## Commands

### Local Development (without Docker)
```bash
# Copy environment template and fill in your local values
cp .env.example .env

# Run with dev profile (requires local PostgreSQL on port 5432)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Local Development (with Docker — preferred)
```bash
# Start full stack (app + PostgreSQL) — one command
docker compose up --build

# Tear down and remove volumes
docker compose down -v
```

### Build & Test
```bash
# Compile only
./mvnw compile

# Run all tests
./mvnw test

# Build fat JAR (skipping tests)
./mvnw package -DskipTests

# Build fat JAR (with tests — use in CI)
./mvnw package
```

### Common Maven Shortcuts
```bash
# Clean and rebuild
./mvnw clean install

# Run a single test class
./mvnw test -Dtest=ProductServiceTest

# Run a single test method
./mvnw test -Dtest=ProductServiceTest#createProduct_success
```

---

## Important Rules

These rules exist to prevent automatic failure on the rubric. Violating them is not negotiable.

### MUST DO
1. **Role-based access control must be enforced** — both at URL level (`SecurityConfig`) and
   at method level (`@PreAuthorize`). Testing as a BUYER on `/admin/**` must return 403.
2. **Never push directly to `main`** — branch protection must be enabled. All merges go via PR
   with at least one approval: `feature/*` → `develop` → `UAT` → `PROD` → `main`.
3. **Both `Dockerfile` and `docker-compose.yml` must be present** and functional. `docker compose
   up --build` must start the full stack with no manual steps.
4. **Minimum 15 unit tests + 3 integration tests** — all must pass in CI before any deployment.
5. **App must be deployed on Render** with a live, publicly accessible URL at submission.
6. **Entities never cross the API boundary** — always map to/from DTOs in the service layer.
7. **No credentials in source code** — all secrets via environment variables, documented in
   `.env.example`.

### NEVER DO
- Mixed import styles (use only `import com.stockmate.*` — never wildcard + specific mix)
- Circular dependencies between packages (controller → service → repository, never the reverse)
- Business logic in controllers or repositories
- Direct entity return from controller methods
- Hardcoded `localhost`, passwords, or secret keys anywhere in tracked files
- `@Autowired` field injection (use constructor injection)

---

## Testing

### Required Counts (CI blocks deployment if these fail)
| Type | Framework | Target | Minimum |
|---|---|---|---|
| Unit | JUnit 5 + Mockito | Service layer | **15** |
| Integration | SpringBootTest + MockMvc | Controller endpoints | **3** |

### Unit Test Cases per Class

**ProductServiceTest** (8 cases)
- `createProduct_success`
- `createProduct_categoryNotFound`
- `updateProduct_notOwner`
- `deleteProduct_asAdmin`
- `getProductById_notFound`
- `getStockStatus_inStock`
- `getStockStatus_preOrder`
- `getStockStatus_outOfStock`

**OrderServiceTest** (7 cases)
- `placeOrder_success`
- `placeOrder_insufficientStock`
- `placeOrder_productNotFound`
- `getOrdersByBuyer`
- `updateOrderStatus_asSellerOwner`
- `cancelOrder_success`
- `cancelOrder_notOwner`

**UserServiceTest** (5 cases)
- `registerUser_success`
- `registerUser_duplicateEmail`
- `loadUserByUsername_notFound`
- `changeRole_success`
- `registerUser_withSellerRole`

**CategoryServiceTest** (3 cases)
- `createCategory_success`
- `createCategory_duplicateName`
- `deleteCategory_withProducts`

### Integration Test Coverage (minimum)
- `AuthControllerTest` — register flow, login flow, logout
- `ProductControllerTest` — list products, create product (SELLER), attempt create as BUYER (403)
- `OrderControllerTest` — place order (BUYER), cancel order (BUYER), stock decrement verification

### Writing Tests
- Unit tests mock all dependencies with `@ExtendWith(MockitoExtension.class)` — no Spring context loaded
- Integration tests use `@SpringBootTest` + `@AutoConfigureMockMvc` with an in-memory or test DB
- Use `@WithMockUser(roles = "BUYER")` to simulate authenticated roles in MockMvc tests
- Test method naming convention: `methodUnderTest_scenario` (e.g. `placeOrder_insufficientStock`)

---

## Environment Variables Reference

All values required at runtime. Document every new variable in `.env.example` immediately.

| Variable | Description | Example |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://db:5432/stockmate` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `stockmate_user` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `changeme` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |

---

## CI/CD Pipeline Summary

| Job | Trigger | Steps |
|---|---|---|
| `build-and-test` | Push to `feature/*`, `UAT`, `PROD`, `main`; PR to same | Checkout → Java 17 → Cache Maven → `mvn test` → Report |
| `deploy-uat` | Push to `UAT` (after `build-and-test` passes) | Trigger Render UAT hook via `secrets.RENDER_DEPLOY_HOOK_UAT` |
| `deploy-prod` | Push to `PROD` (after `build-and-test` passes) | Trigger Render PROD hook via `secrets.RENDER_DEPLOY_HOOK_PROD` |
| `deploy-main` | Push to `main` (after `build-and-test` passes) | Trigger Render production hook via `secrets.RENDER_DEPLOY_HOOK` |

**Required GitHub Secrets:** `RENDER_DEPLOY_HOOK_UAT`, `RENDER_DEPLOY_HOOK_PROD`, `RENDER_DEPLOY_HOOK`

---

*Last updated: initial project setup. Update this file whenever architecture decisions change.*
