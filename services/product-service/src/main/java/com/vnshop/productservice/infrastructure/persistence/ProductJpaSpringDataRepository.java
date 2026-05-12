package com.vnshop.productservice.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductJpaSpringDataRepository extends JpaRepository<ProductJpaEntity, UUID> {
    List<ProductJpaEntity> findBySellerId(String sellerId);

    List<ProductJpaEntity> findByCategoryId(String categoryId);

    @Query("select product from ProductJpaEntity product where lower(product.name) like lower(concat('%', :name, '%'))")
    List<ProductJpaEntity> searchByName(@Param("name") String name);

    @Query("select distinct product.categoryId from ProductJpaEntity product where product.categoryId is not null")
    List<String> findDistinctCategories();
}
