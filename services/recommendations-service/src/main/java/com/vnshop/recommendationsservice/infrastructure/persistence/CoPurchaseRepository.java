package com.vnshop.recommendationsservice.infrastructure.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoPurchaseRepository
        extends JpaRepository<CoPurchaseJpaEntity, CoPurchaseJpaEntity.CoPurchaseId> {

    /**
     * Top-N co-purchased products for a given source product. The
     * {@code (product_a, co_count DESC)} index makes this an indexed
     * range scan rather than a sort.
     */
    @Query("select c from CoPurchaseJpaEntity c where c.id.productA = :productA order by c.coCount desc")
    List<CoPurchaseJpaEntity> findTopByProductA(@Param("productA") String productA, Pageable pageable);
}
