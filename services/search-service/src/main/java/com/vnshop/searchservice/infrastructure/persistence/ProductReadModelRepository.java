package com.vnshop.searchservice.infrastructure.persistence;

import com.vnshop.searchservice.domain.ProductReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select product from ProductReadModelJpaEntity product
            where (:query is null or lower(product.name) like lower(concat('%', :query, '%')) or lower(product.description) like lower(concat('%', :query, '%')))
              and (:categoryId is null or product.categoryId = :categoryId)
              and (:brand is null or product.brand = :brand)
              and (:minPrice is null or product.maxPrice >= :minPrice)
              and (:maxPrice is null or product.minPrice <= :maxPrice)
            """)
    Page<ProductReadModelJpaEntity> searchEntitiesPaged(
            @Param("query") String query,
            @Param("categoryId") String categoryId,
            @Param("brand") String brand,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );

    @Query("select distinct product.categoryId from ProductReadModelJpaEntity product where product.categoryId is not null")
    List<String> findDistinctCategories();

    /** Prefix-match on name for header autocomplete. Uses idx_product_read_models_name_lower. */
    @Query("""
            select product.name from ProductReadModelJpaEntity product
            where lower(product.name) like lower(concat(:prefix, '%'))
            order by product.name asc
            """)
    List<String> findSuggestions(@Param("prefix") String prefix, Pageable pageable);

    /**
     * Facet aggregation queries scoped by the same WHERE clause as the main
     * search. Returns Object[] of (key, count) so the use case can build the
     * response shape without exposing JPA internals.
     */
    @Query("""
            select product.categoryId, count(product) from ProductReadModelJpaEntity product
            where (:query is null or lower(product.name) like lower(concat('%', :query, '%')) or lower(product.description) like lower(concat('%', :query, '%')))
              and (:brand is null or product.brand = :brand)
              and (:minPrice is null or product.maxPrice >= :minPrice)
              and (:maxPrice is null or product.minPrice <= :maxPrice)
              and product.categoryId is not null
            group by product.categoryId
            order by count(product) desc, product.categoryId asc
            """)
    List<Object[]> categoryFacets(
            @Param("query") String query,
            @Param("brand") String brand,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );

    @Query("""
            select product.brand, count(product) from ProductReadModelJpaEntity product
            where (:query is null or lower(product.name) like lower(concat('%', :query, '%')) or lower(product.description) like lower(concat('%', :query, '%')))
              and (:categoryId is null or product.categoryId = :categoryId)
              and (:minPrice is null or product.maxPrice >= :minPrice)
              and (:maxPrice is null or product.minPrice <= :maxPrice)
              and product.brand is not null
            group by product.brand
            order by count(product) desc, product.brand asc
            """)
    List<Object[]> brandFacets(
            @Param("query") String query,
            @Param("categoryId") String categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );

    default List<ProductReadModel> search(String query, String categoryId, String brand, BigDecimal minPrice, BigDecimal maxPrice) {
        return searchEntities(blankToNull(query), blankToNull(categoryId), blankToNull(brand), minPrice, maxPrice)
                .stream()
                .map(ProductReadModelJpaEntity::toDomain)
                .toList();
    }

    default Page<ProductReadModel> searchPaged(String query, String categoryId, String brand, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        return searchEntitiesPaged(blankToNull(query), blankToNull(categoryId), blankToNull(brand), minPrice, maxPrice, pageable)
                .map(ProductReadModelJpaEntity::toDomain);
    }

    default List<String> suggestions(String prefix, Pageable pageable) {
        String normalized = blankToNull(prefix);
        if (normalized == null) {
            return List.of();
        }
        return findSuggestions(normalized, pageable);
    }

    default List<Object[]> categoryFacetsFor(String query, String brand, BigDecimal minPrice, BigDecimal maxPrice) {
        return categoryFacets(blankToNull(query), blankToNull(brand), minPrice, maxPrice);
    }

    default List<Object[]> brandFacetsFor(String query, String categoryId, BigDecimal minPrice, BigDecimal maxPrice) {
        return brandFacets(blankToNull(query), blankToNull(categoryId), minPrice, maxPrice);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
