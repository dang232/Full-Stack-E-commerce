package com.vnshop.searchservice.infrastructure.persistence;

import com.vnshop.searchservice.domain.ProductReadModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductReadModelRepository extends JpaRepository<ProductReadModelJpaEntity, String> {
    @Query("""
            select product from ProductReadModelJpaEntity product
            where (:query is null or lower(product.name) like lower(concat('%', :query, '%')) or lower(product.description) like lower(concat('%', :query, '%')))
              and (:categoryId is null or product.categoryId = :categoryId)
              and (:brand is null or product.brand = :brand)
              and (:minPrice is null or product.maxPrice >= :minPrice)
              and (:maxPrice is null or product.minPrice <= :maxPrice)
            """)
    List<ProductReadModelJpaEntity> searchEntities(
            @Param("query") String query,
            @Param("categoryId") String categoryId,
            @Param("brand") String brand,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );

    @Query("select distinct product.categoryId from ProductReadModelJpaEntity product where product.categoryId is not null")
    List<String> findDistinctCategories();

    default List<ProductReadModel> search(String query, String categoryId, String brand, BigDecimal minPrice, BigDecimal maxPrice) {
        return searchEntities(blankToNull(query), blankToNull(categoryId), blankToNull(brand), minPrice, maxPrice)
                .stream()
                .map(ProductReadModelJpaEntity::toDomain)
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
