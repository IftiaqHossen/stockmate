# StockMate

StockMate is a role-based inventory management web application built with Spring Boot, Thymeleaf, Spring Security, and PostgreSQL.
It supports three user roles:

- `ROLE_ADMIN`
- `ROLE_SELLER`
- `ROLE_BUYER`

The project demonstrates end-to-end full-stack workflow including secure auth, layered architecture, Docker setup, automated tests, and Render deployment.

## Features

- Secure registration and login with Spring Security
- Role-based dashboards and route protection
- Product management for sellers
- Category management for admins
- Order lifecycle for buyers, sellers, and admins
- Admin user management (role change, disable, delete)
- Global exception handling with dedicated error pages

## Tech Stack

- Java 17
- Spring Boot 3.5.12
- Spring Data JPA
- Spring Security
- Thymeleaf
- PostgreSQL
- Maven Wrapper (`mvnw` / `mvnw.cmd`)
- Docker and Docker Compose
- GitHub Actions (CI + branch deploy hooks)

## Architecture

Layered architecture is used throughout:

- Controller layer: request handling and view rendering
- Service layer: business rules and authorization checks
- Repository layer: persistence access
- Model layer: JPA entities and enums

Key directories:

- `src/main/java/com/stockmate/stockmate/controller`
- `src/main/java/com/stockmate/stockmate/service`
- `src/main/java/com/stockmate/stockmate/repository`
- `src/main/java/com/stockmate/stockmate/model`
- `src/main/resources/templates`

## Database and Seed Data

Schema and base seed SQL scripts:

- `docker/init.sql`
- `stockmate_db.sql`

These SQL files seed:

- Roles (`ROLE_ADMIN`, `ROLE_SELLER`, `ROLE_BUYER`)
- Default admin user
- Sample categories

Runtime seed initializers are also included for cloud resilience:

- `RoleDataInitializer` ensures required roles exist on startup
- `CategoryDataInitializer` seeds default categories if the category table is empty

This prevents production issues like:

- `Role not found: ROLE_SELLER`
- Empty category dropdowns

## Local Development

### Prerequisites

- Docker Desktop (recommended path)
- OR Java 17 + Maven + PostgreSQL for manual run

### Option A: Run with Docker Compose (Recommended)

1. Create/update `.env` in repository root:

```env
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=1233
```

2. Start stack:

```bash
docker compose up --build -d
```

3. Open app:

- `http://localhost:8080/auth/login`

4. Stop stack:

```bash
docker compose down
```

5. Reset DB volume (if credentials or schema drift):

```bash
docker compose down -v --remove-orphans
docker compose up --build -d
```

### Option B: Run with Maven

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

## Testing

Run all tests:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

The test profile uses in-memory H2 for CI compatibility.

## CI/CD Workflows

Workflows are in `.github/workflows`:

- `build-and-test.yml`
- `deploy-main.yml`
- `deploy-prod.yml`
- `deploy-uat.yml`

CI pipeline behavior:

- Java 17 setup
- Maven test execution
- Surefire report upload

Deploy workflows trigger Render hooks after tests pass.

## Render Deployment

`render.yaml` is configured for:

- service type: web
- runtime: docker
- branch: `main`
- region: singapore

Required environment variables in Render service:

- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `DATABASE_URL` (Render DB connection string)

The app builds JDBC URL from `DATABASE_URL` and uses production profile.

## Branch Strategy

Current branch model in workflows:

- `feature/**` for feature work
- `develop`
- `UAT`
- `PROD`
- `main`

If Render is bound to `main`, merge PRs into `main` to deploy there.

## Troubleshooting

### 1) Category dropdown empty in cloud

Cause: SQL seed file not executed in cloud environment.  
Fix: `CategoryDataInitializer` seeds defaults when category table is empty.

### 2) `Role not found: ROLE_SELLER`

Cause: roles table empty in deployed database.  
Fix: `RoleDataInitializer` ensures required roles on startup.

### 3) Docker error: container name already in use

Fix:

```bash
docker rm -f stockmate_app stockmate_db
docker compose up --build -d
```

### 4) GitHub Actions: `./mvnw: Permission denied`

Fix is already applied in workflows using:

```bash
chmod +x mvnw
```

### 5) Deploy hook malformed URL in Actions

Fix is already applied with secret sanitization and URL validation in deploy workflows.

## License

This project is provided for educational and portfolio use.
