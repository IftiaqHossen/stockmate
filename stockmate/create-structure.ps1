# create-structure.ps1
# Run from project root (where pom.xml is located)

# Define the relative paths of all required files (including directories)
$paths = @(
    ".claude/settings.json",
    ".github/workflows/build-and-test.yml",
    ".github/workflows/deploy-uat.yml",
    ".github/workflows/deploy-prod.yml",
    ".github/workflows/deploy-main.yml",
    "src/main/java/com/stockmate/stockmate/StockmateApplication.java",
    "src/main/java/com/stockmate/stockmate/controller/AuthController.java",
    "src/main/java/com/stockmate/stockmate/controller/ProductController.java",
    "src/main/java/com/stockmate/stockmate/controller/OrderController.java",
    "src/main/java/com/stockmate/stockmate/controller/CategoryController.java",
    "src/main/java/com/stockmate/stockmate/controller/AdminController.java",
    "src/main/java/com/stockmate/stockmate/service/UserService.java",
    "src/main/java/com/stockmate/stockmate/service/UserServiceImpl.java",
    "src/main/java/com/stockmate/stockmate/service/ProductService.java",
    "src/main/java/com/stockmate/stockmate/service/ProductServiceImpl.java",
    "src/main/java/com/stockmate/stockmate/service/OrderService.java",
    "src/main/java/com/stockmate/stockmate/service/OrderServiceImpl.java",
    "src/main/java/com/stockmate/stockmate/service/CategoryService.java",
    "src/main/java/com/stockmate/stockmate/service/CategoryServiceImpl.java",
    "src/main/java/com/stockmate/stockmate/repository/UserRepository.java",
    "src/main/java/com/stockmate/stockmate/repository/ProductRepository.java",
    "src/main/java/com/stockmate/stockmate/repository/OrderRepository.java",
    "src/main/java/com/stockmate/stockmate/repository/CategoryRepository.java",
    "src/main/java/com/stockmate/stockmate/model/User.java",
    "src/main/java/com/stockmate/stockmate/model/Role.java",
    "src/main/java/com/stockmate/stockmate/model/Product.java",
    "src/main/java/com/stockmate/stockmate/model/Order.java",
    "src/main/java/com/stockmate/stockmate/model/Category.java",
    "src/main/java/com/stockmate/stockmate/model/enums/OrderStatus.java",
    "src/main/java/com/stockmate/stockmate/model/enums/ProductStatus.java",
    "src/main/java/com/stockmate/stockmate/dto/request/RegisterRequest.java",
    "src/main/java/com/stockmate/stockmate/dto/request/LoginRequest.java",
    "src/main/java/com/stockmate/stockmate/dto/request/CreateProductRequest.java",
    "src/main/java/com/stockmate/stockmate/dto/request/UpdateProductRequest.java",
    "src/main/java/com/stockmate/stockmate/dto/request/PlaceOrderRequest.java",
    "src/main/java/com/stockmate/stockmate/dto/request/UpdateOrderStatusRequest.java",
    "src/main/java/com/stockmate/stockmate/dto/request/CreateCategoryRequest.java",
    "src/main/java/com/stockmate/stockmate/dto/response/UserResponse.java",
    "src/main/java/com/stockmate/stockmate/dto/response/ProductResponse.java",
    "src/main/java/com/stockmate/stockmate/dto/response/OrderResponse.java",
    "src/main/java/com/stockmate/stockmate/dto/response/CategoryResponse.java",
    "src/main/java/com/stockmate/stockmate/security/SecurityConfig.java",
    "src/main/java/com/stockmate/stockmate/security/CustomUserDetailsService.java",
    "src/main/java/com/stockmate/stockmate/security/CustomUserDetails.java",
    "src/main/java/com/stockmate/stockmate/exception/ResourceNotFoundException.java",
    "src/main/java/com/stockmate/stockmate/exception/InsufficientStockException.java",
    "src/main/java/com/stockmate/stockmate/exception/GlobalExceptionHandler.java",
    "src/main/java/com/stockmate/stockmate/config/AppConfig.java",
    "src/test/java/com/stockmate/stockmate/service/ProductServiceTest.java",
    "src/test/java/com/stockmate/stockmate/service/OrderServiceTest.java",
    "src/test/java/com/stockmate/stockmate/service/UserServiceTest.java",
    "src/test/java/com/stockmate/stockmate/service/CategoryServiceTest.java",
    "src/test/java/com/stockmate/stockmate/controller/AuthControllerTest.java",
    "src/test/java/com/stockmate/stockmate/controller/ProductControllerTest.java",
    "src/test/java/com/stockmate/stockmate/controller/OrderControllerTest.java",
    "src/main/resources/application.properties",
    "src/main/resources/application-dev.properties",
    "src/main/resources/application-prod.properties",
    "src/main/resources/templates/auth/login.html",
    "src/main/resources/templates/auth/register.html",
    "src/main/resources/templates/products/catalogue.html",
    "src/main/resources/templates/products/detail.html",
    "src/main/resources/templates/products/form.html",
    "src/main/resources/templates/orders/buyer-orders.html",
    "src/main/resources/templates/orders/seller-orders.html",
    "src/main/resources/templates/admin/dashboard.html",
    "src/main/resources/templates/admin/users.html",
    "src/main/resources/templates/admin/categories.html",
    "src/main/resources/templates/dashboard/seller.html",
    "src/main/resources/templates/dashboard/buyer.html",
    "src/main/resources/templates/error/403.html",
    "src/main/resources/templates/fragments/header.html",
    "src/main/resources/templates/fragments/footer.html",
    "src/main/resources/static/css/main.css",
    "src/main/resources/static/js/main.js",
    "docker/init.sql",
    "docs/architecture-diagram.png",
    "docs/er-diagram.png",
    "Dockerfile",
    "docker-compose.yml",
    ".env.example",
    ".gitignore",
    "pom.xml",
    "README.md",
    "CLAUDE.md"
)

# Helper function to get package path from a Java file path
function Get-JavaPackagePath {
    param([string]$javaFilePath)
    # Relative path from src/main/java or src/test/java
    if ($javaFilePath -like "src/main/java/*") {
        $base = "src/main/java/"
    } elseif ($javaFilePath -like "src/test/java/*") {
        $base = "src/test/java/"
    } else {
        return $null
    }
    $relative = $javaFilePath.Substring($base.Length)
    # Remove the filename (everything after last slash)
    $dir = Split-Path $relative -Parent
    # Replace slashes with dots
    $package = $dir -replace '\\', '.' -replace '/', '.'
    return $package
}

# Create directories and files
foreach ($path in $paths) {
    $fullPath = Join-Path -Path (Get-Location) -ChildPath $path
    $parent = Split-Path $fullPath -Parent

    # Create parent directory if it doesn't exist
    if (-not (Test-Path $parent)) {
        New-Item -Path $parent -ItemType Directory -Force | Out-Null
        Write-Host "Created directory: $parent"
    }

    # Create file only if it doesn't exist
    if (-not (Test-Path $fullPath)) {
        if ($path -like "*.java") {
            # Java file: write package declaration only
            $package = Get-JavaPackagePath $path
            if ($package) {
                $content = "package $package;`n"
            } else {
                $content = ""
            }
            # Use UTF8 without BOM
            $utf8NoBom = New-Object System.Text.UTF8Encoding $false
            [System.IO.File]::WriteAllText($fullPath, $content, $utf8NoBom)
            Write-Host "Created Java file with package: $fullPath"
        } else {
            # Non-Java file: create empty
            New-Item -Path $fullPath -ItemType File -Force | Out-Null
            Write-Host "Created empty file: $fullPath"
        }
    } else {
        Write-Host "File already exists, skipped: $fullPath"
    }
}

Write-Host "Structure creation completed."