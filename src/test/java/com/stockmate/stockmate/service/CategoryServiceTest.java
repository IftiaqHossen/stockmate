package com.stockmate.stockmate.service;

import com.stockmate.stockmate.dto.request.CreateCategoryRequest;
import com.stockmate.stockmate.dto.response.CategoryResponse;
import com.stockmate.stockmate.exception.ResourceNotFoundException;
import com.stockmate.stockmate.model.Category;
import com.stockmate.stockmate.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for CategoryServiceImpl.
 *
 * Categories are Admin-only managed (FR-CAT-01) and act as a required FK for
 * every product. These tests protect the two most dangerous operations:
 * duplicate name creation and deletion of a category that still has products.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    // ── TC-1: createCategory_success ──────────────────────────────────────────
    /**
     * WHY: Happy path — Admin creates a new unique category. Validates that the
     * name uniqueness check passes and the entity is persisted.
     */
    @Test
    @DisplayName("createCategory: unique name persists category and returns CategoryResponse")
    void createCategory_success() {
        Category electronicsCategory = new Category("Electronics", "Electronic gadgets and devices");

        CreateCategoryRequest request = new CreateCategoryRequest(
                "Electronics", "Electronic gadgets and devices");

        given(categoryRepository.existsByName("Electronics")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(electronicsCategory);

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Electronics");
        then(categoryRepository).should().save(any(Category.class));
    }

    // ── TC-2: createCategory_duplicateName ────────────────────────────────────
    /**
     * WHY: Category names must be UNIQUE (DB constraint: UNIQUE, NOT NULL). If
     * the service doesn't guard this, we'd get a
     * DataIntegrityViolationException from the DB instead of a clean,
     * user-friendly validation error. Catching it here gives us a controlled
     * 400 response instead of a 500.
     */
    @Test
    @DisplayName("createCategory: duplicate name throws IllegalArgumentException without saving")
    void createCategory_duplicateName() {
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", "");

        given(categoryRepository.existsByName("Electronics")).willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        then(categoryRepository).should(never()).save(any());
    }

    // ── TC-3: deleteCategory_withProducts ─────────────────────────────────────
    /**
     * WHY: Deleting a category that still has products assigned would orphan
     * those products (null FK) or cascade-delete them — either outcome destroys
     * seller data. The service must prevent deletion and surface a clear error
     * instead.
     *
     * This is the most destructive edge case in category management and is a
     * likely production failure if not guarded.
     */
    @Test
    @DisplayName("deleteCategory: category with assigned products throws IllegalStateException")
    void deleteCategory_withProducts() {
        Category electronicsCategory = new Category("Electronics", "Electronic gadgets and devices");

        given(categoryRepository.findById(1L)).willReturn(Optional.of(electronicsCategory));
        given(categoryRepository.hasProducts(1L)).willReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("products");

        then(categoryRepository).should(never()).delete(any());
    }

    // ── BONUS TC-4: deleteCategory_notFound ───────────────────────────────────
    /**
     * WHY: Admin tries to delete a category ID that no longer exists (e.g.,
     * deleted by another admin in a parallel session). Must return 404, not
     * NPE.
     */
    @Test
    @DisplayName("deleteCategory: non-existent category throws ResourceNotFoundException")
    void deleteCategory_notFound() {
        given(categoryRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");

        then(categoryRepository).should(never()).delete(any());
    }
}
