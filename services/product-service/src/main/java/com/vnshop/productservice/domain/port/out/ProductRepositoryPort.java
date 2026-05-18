package com.vnshop.productservice.domain.port.out;

import com.vnshop.productservice.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProductRepositoryPort {
    Product save(Product product);

    Optional<Product> findById(UUID productId);

    List<Product> findBySellerId(String sellerId);

    List<Product> findByCategory(String categoryId);

    List<Product> searchByName(String name);

    List<String> findDistinctCategories();

    /**
     * Paged catalog query used by the buyer-facing GET /products endpoint.
     * <p>{@code categoryId}, {@code q}, and {@code sellerId} are all optional;
     * null/blank means the corresponding filter is skipped.
     */
    Page<Product> findCatalog(String categoryId, String q, String sellerId, Pageable pageable);

    long countBySellerId(String sellerId);

    Map<String, Long> countBySellerIds(Set<String> sellerIds);
}
