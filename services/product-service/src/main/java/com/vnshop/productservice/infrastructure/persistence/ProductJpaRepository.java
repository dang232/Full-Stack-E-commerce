package com.vnshop.productservice.infrastructure.persistence;

import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

interface ProductJpaSpringDataRepository extends JpaRepository<ProductJpaEntity, UUID> {
    List<ProductJpaEntity> findBySellerId(String sellerId);

    List<ProductJpaEntity> findByCategoryId(String categoryId);

    @Query("select product from ProductJpaEntity product where lower(product.name) like lower(concat('%', :name, '%'))")
    List<ProductJpaEntity> searchByName(@Param("name") String name);

    @Query("select distinct product.categoryId from ProductJpaEntity product where product.categoryId is not null")
    List<String> findDistinctCategories();
}
