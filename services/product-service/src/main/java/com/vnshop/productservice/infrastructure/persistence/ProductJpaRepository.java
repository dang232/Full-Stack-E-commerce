package com.vnshop.productservice.infrastructure.persistence;

import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductJpaRepository implements ProductRepositoryPort {
    private final ProductJpaSpringDataRepository springDataRepository;

    public ProductJpaRepository(ProductJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Product save(Product product) {
        return springDataRepository.save(ProductJpaEntity.fromDomain(product)).toDomain();
    }

    @Override
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
}
