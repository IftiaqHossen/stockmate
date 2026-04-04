-- ============================================================
-- StockMate — PostgreSQL DDL Script
-- Database : stockmate_db
-- Run this entire script once in pgAdmin 4 Query Tool
-- ============================================================

-- Drop tables in reverse dependency order (safe re-run)
DROP TABLE IF EXISTS orders        CASCADE;
DROP TABLE IF EXISTS products      CASCADE;
DROP TABLE IF EXISTS user_roles    CASCADE;
DROP TABLE IF EXISTS categories    CASCADE;
DROP TABLE IF EXISTS roles         CASCADE;
DROP TABLE IF EXISTS users         CASCADE;

-- Drop custom types if they exist (safe re-run)
DROP TYPE IF EXISTS product_status_enum;
DROP TYPE IF EXISTS order_status_enum;

-- ── Custom ENUM types ────────────────────────────────────────
-- Matches ProductStatus.java enum values
CREATE TYPE product_status_enum AS ENUM (
    'ACTIVE',
    'DISCONTINUED'
);

-- Matches OrderStatus.java enum values
CREATE TYPE order_status_enum AS ENUM (
    'PENDING',
    'CONFIRMED',
    'SHIPPED',
    'DELIVERED',
    'CANCELLED'
);

-- ============================================================
-- TABLE: users
-- Stores all registered users (ADMIN, SELLER, BUYER)
-- ============================================================
CREATE TABLE users (
                       id          BIGSERIAL       PRIMARY KEY,
                       username    VARCHAR(50)     NOT NULL UNIQUE,
                       email       VARCHAR(100)    NOT NULL UNIQUE,
                       password    VARCHAR(255)    NOT NULL,               -- BCrypt hash
                       enabled     BOOLEAN         NOT NULL DEFAULT TRUE,  -- Admin can disable
                       created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TABLE: roles
-- Lookup table — seeded below (ROLE_ADMIN, ROLE_SELLER, ROLE_BUYER)
-- ============================================================
CREATE TABLE roles (
                       id      BIGSERIAL       PRIMARY KEY,
                       name    VARCHAR(20)     NOT NULL UNIQUE             -- ROLE_ADMIN | ROLE_SELLER | ROLE_BUYER
);

-- ============================================================
-- TABLE: user_roles  (M:M join table — users ↔ roles)
-- ============================================================
CREATE TABLE user_roles (
                            user_id     BIGINT  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role_id     BIGINT  NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                            PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- TABLE: categories
-- Managed by ADMIN only
-- ============================================================
CREATE TABLE categories (
                            id          BIGSERIAL       PRIMARY KEY,
                            name        VARCHAR(100)    NOT NULL UNIQUE,
                            description TEXT
);

-- ============================================================
-- TABLE: products
-- Created by SELLER; stock_status is NOT stored (computed in service)
-- ============================================================
CREATE TABLE products (
                          id              BIGSERIAL               PRIMARY KEY,
                          name            VARCHAR(200)            NOT NULL,
                          description     TEXT,
                          price           DECIMAL(10, 2)          NOT NULL CHECK (price >= 0),
                          stock_quantity  INT                     NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
                          status          product_status_enum     NOT NULL DEFAULT 'ACTIVE',  -- drives stock status display
                          category_id     BIGINT                  NOT NULL REFERENCES categories(id),
                          seller_id       BIGINT                  NOT NULL REFERENCES users(id),
                          created_at      TIMESTAMP               NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TABLE: orders
-- Placed by BUYER; status transitions managed by SELLER/ADMIN
-- ============================================================
CREATE TABLE orders (
                        id          BIGSERIAL           PRIMARY KEY,
                        buyer_id    BIGINT              NOT NULL REFERENCES users(id),
                        product_id  BIGINT              NOT NULL REFERENCES products(id),
                        quantity    INT                 NOT NULL CHECK (quantity > 0),
                        total_price DECIMAL(10, 2)      NOT NULL CHECK (total_price >= 0),  -- quantity × product.price at time of order
                        status      order_status_enum   NOT NULL DEFAULT 'PENDING',
                        ordered_at  TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INDEXES — improve query performance for common lookups
-- ============================================================
CREATE INDEX idx_products_seller_id    ON products(seller_id);
CREATE INDEX idx_products_category_id  ON products(category_id);
CREATE INDEX idx_orders_buyer_id       ON orders(buyer_id);
CREATE INDEX idx_orders_product_id     ON orders(product_id);
CREATE INDEX idx_orders_status         ON orders(status);
CREATE INDEX idx_users_username        ON users(username);
CREATE INDEX idx_users_email           ON users(email);

-- ============================================================
-- SEED DATA — roles (required for application startup)
-- These 3 rows must always exist before the app boots
-- ============================================================
INSERT INTO roles (name) VALUES
                             ('ROLE_ADMIN'),
                             ('ROLE_SELLER'),
                             ('ROLE_BUYER');

-- ============================================================
-- SEED DATA — default ADMIN user
-- Username : admin
-- Password : admin123  (BCrypt hash below — change in production)
-- Generated with: BCryptPasswordEncoder().encode("admin123")
-- ============================================================
INSERT INTO users (username, email, password, enabled) VALUES
    ('admin', 'admin@stockmate.com', '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE);

-- Assign ROLE_ADMIN to the default admin user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM   users u, roles r
WHERE  u.username = 'admin'
  AND    r.name     = 'ROLE_ADMIN';

-- ============================================================
-- SEED DATA — sample categories (optional, for dev testing)
-- ============================================================
INSERT INTO categories (name, description) VALUES
                                               ('Electronics',  'Electronic devices and accessories'),
                                               ('Clothing',     'Apparel and fashion items'),
                                               ('Books',        'Physical and digital books'),
                                               ('Home & Garden','Furniture, tools, and garden supplies'),
                                               ('Sports',       'Sports equipment and outdoor gear');

-- ============================================================
-- VERIFICATION — run these SELECT statements to confirm setup
-- ============================================================
SELECT 'users'      AS tbl, COUNT(*) AS rows FROM users
UNION ALL
SELECT 'roles',              COUNT(*)         FROM roles
UNION ALL
SELECT 'user_roles',         COUNT(*)         FROM user_roles
UNION ALL
SELECT 'categories',         COUNT(*)         FROM categories
UNION ALL
SELECT 'products',           COUNT(*)         FROM products
UNION ALL
SELECT 'orders',             COUNT(*)         FROM orders;