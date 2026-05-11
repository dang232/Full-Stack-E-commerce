package com.vnshop.productservice.domain.port.out;

import com.vnshop.productservice.domain.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {
    Product save(Product product);

    Optional<Product> findById(UUID productId);

    List<Product> findBySellerId(String sellerId);

    List<Product> findByCategory(String categoryId);

    List<Product> searchByName(String name);

    List<String> findDistinctCategories();
}
