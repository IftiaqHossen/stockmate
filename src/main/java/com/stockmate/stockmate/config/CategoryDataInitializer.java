package com.stockmate.stockmate.config;

import com.stockmate.stockmate.model.Category;
import com.stockmate.stockmate.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds default categories when the categories table is empty.
 * This mirrors docker/init.sql baseline data for cloud environments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryDataInitializer implements ApplicationRunner {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            return;
        }

        List<Category> defaults = List.of(
                new Category("Electronics", "Electronic devices and accessories"),
                new Category("Clothing", "Apparel and fashion items"),
                new Category("Books", "Physical and digital books"),
                new Category("Home & Garden", "Furniture, tools, and garden supplies"),
                new Category("Sports", "Sports equipment and outdoor gear")
        );

        categoryRepository.saveAll(defaults);
        log.info("Seeded default categories: {}", defaults.size());
    }
}
