package com.vnshop.productservice.infrastructure.persistence;

import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class ProductJpaRepository implements ProductRepositoryPort {
    private final ProductJpaSpringDataRepository springDataRepository;

    public ProductJpaRepository(ProductJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @CacheEvict(value = "product", key = "#product.productId()")
    public Product save(Product product) {
        return springDataRepository.save(ProductJpaEntity.fromDomain(product)).toDomain();
    }

    @Override
    @Cacheable(value = "product", key = "#productId", unless = "#result == null")
    public Optional<Product> findById(UUID productId) {
        return springDataRepository.findById(productId).map(ProductJpaEntity::toDomain);
    }

    @Override
    public List<Product> findBySellerId(String sellerId) {
        return springDataRepository.findBySellerId(sellerId).stream().map(ProductJpaEntity::toDomain).toList();
    }

    @Override
    public List<Product> findByCategory(String categoryId) {
        return springDataRepository.findByCategoryId(categoryId).stream().map(ProductJpaEntity::toDomain).toList();
    }

    @Override
    public List<Product> searchByName(String name) {
        return springDataRepository.searchByName(name == null ? "" : name).stream().map(ProductJpaEntity::toDomain).toList();
    }

    @Override
    public List<String> findDistinctCategories() {
        return springDataRepository.findDistinctCategories();
    }

    @Override
    public Page<Product> findCatalog(String categoryId, String q, String sellerId, Pageable pageable) {
        String normalizedCategory = (categoryId == null || categoryId.isBlank()) ? null : categoryId;
        String normalizedQuery = (q == null || q.isBlank()) ? null : q;
        String normalizedSeller = (sellerId == null || sellerId.isBlank()) ? null : sellerId;
        return springDataRepository.findCatalog(normalizedCategory, normalizedQuery, normalizedSeller, pageable)
                .map(ProductJpaEntity::toDomain);
    }

    @Override
    public long countBySellerId(String sellerId) {
        return springDataRepository.countBySellerId(sellerId);
    }

    @Override
    public Map<String, Long> countBySellerIds(Set<String> sellerIds) {
        Map<String, Long> result = new HashMap<>();
        // Pre-fill all requested sellers with zero defaults
        for (String id : sellerIds) {
            result.put(id, 0L);
        }
        if (sellerIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = springDataRepository.countBySellerIds(sellerIds);
        for (Object[] row : rows) {
            String sellerId = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            result.put(sellerId, count);
        }
        return result;
    }
}
