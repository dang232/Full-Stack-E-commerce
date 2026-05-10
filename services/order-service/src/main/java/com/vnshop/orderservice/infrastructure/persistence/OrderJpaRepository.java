package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderJpaRepository implements OrderRepositoryPort {
    private final OrderJpaSpringDataRepository springDataRepository;

    public OrderJpaRepository(OrderJpaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Order save(Order order) {
        return springDataRepository.save(OrderJpaEntity.fromDomain(order)).toDomain();
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return springDataRepository.findById(orderId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return springDataRepository.findByOrderNumber(orderNumber).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return springDataRepository.findByIdempotencyKey(idempotencyKey).map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findByBuyerId(String buyerId) {
        return springDataRepository.findByBuyerId(buyerId).stream().map(OrderJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Order> findBySubOrderId(Long subOrderId) {
        return springDataRepository.findBySubOrderId(subOrderId).map(OrderJpaEntity::toDomain);
    }

    public Optional<String> findOrderIdBySubOrderId(Long subOrderId) {
        return springDataRepository.findOrderIdBySubOrderId(subOrderId);
    }

    public List<Order> findBySellerIdAndFulfillmentStatus(String sellerId, com.vnshop.orderservice.domain.FulfillmentStatus status) {
        return springDataRepository.findBySellerIdAndFulfillmentStatus(sellerId, status).stream().map(OrderJpaEntity::toDomain).toList();
    }

    public long countByDateBetween(LocalDate startDate, LocalDate endDate) {
        return springDataRepository.countByCreatedAtBetween(startInstant(startDate), endInstant(endDate));
    }

    public BigDecimal sumRevenueByDateBetween(LocalDate startDate, LocalDate endDate) {
        return springDataRepository.sumRevenueByCreatedAtBetween(startInstant(startDate), endInstant(endDate));
    }

    public long countDistinctBuyerId(LocalDate startDate, LocalDate endDate) {
        return springDataRepository.countDistinctBuyerIdByCreatedAtBetween(startInstant(startDate), endInstant(endDate));
    }

    public long countDistinctSellerId(LocalDate startDate, LocalDate endDate) {
        return springDataRepository.countDistinctSellerIdByCreatedAtBetween(startInstant(startDate), endInstant(endDate));
    }

    public List<RevenueByDate> revenueByDateBetween(LocalDate startDate, LocalDate endDate) {
        return springDataRepository.revenueByDateBetween(startInstant(startDate), endInstant(endDate));
    }

    public List<TopMetric> topProducts(int limit) {
        return springDataRepository.topProducts(PageRequest.of(0, limit));
    }

    public List<TopMetric> topSellers(int limit) {
        return springDataRepository.topSellers(PageRequest.of(0, limit));
    }

    private static Instant startInstant(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Instant endInstant(LocalDate date) {
        return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1);
    }

    public record RevenueByDate(LocalDate date, BigDecimal revenue) {
    }

    public record TopMetric(String id, String name, BigDecimal value) {
        public TopMetric(String id, String name, Number value) {
            this(id, name, BigDecimal.valueOf(value.longValue()));
        }
    }
}

interface OrderJpaSpringDataRepository extends JpaRepository<OrderJpaEntity, String> {
    Optional<OrderJpaEntity> findByOrderNumber(String orderNumber);

    Optional<OrderJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<OrderJpaEntity> findByBuyerId(String buyerId);

    @Query("select subOrder.order.id from SubOrderJpaEntity subOrder where subOrder.id = :subOrderId")
    Optional<String> findOrderIdBySubOrderId(@Param("subOrderId") Long subOrderId);

    @Query("select subOrder.order from SubOrderJpaEntity subOrder where subOrder.id = :subOrderId")
    Optional<OrderJpaEntity> findBySubOrderId(@Param("subOrderId") Long subOrderId);

    @Query("select distinct subOrder.order from SubOrderJpaEntity subOrder where subOrder.sellerId = :sellerId and subOrder.fulfillmentStatus = :status")
    List<OrderJpaEntity> findBySellerIdAndFulfillmentStatus(
            @Param("sellerId") String sellerId,
            @Param("status") com.vnshop.orderservice.domain.FulfillmentStatus status
    );

    long countByCreatedAtBetween(Instant startInclusive, Instant endInclusive);

    @Query("select coalesce(sum(order.finalAmount.amount), 0) from OrderJpaEntity order where order.createdAt between :startInclusive and :endInclusive")
    BigDecimal sumRevenueByCreatedAtBetween(
            @Param("startInclusive") Instant startInclusive,
            @Param("endInclusive") Instant endInclusive
    );

    @Query("select count(distinct order.buyerId) from OrderJpaEntity order where order.createdAt between :startInclusive and :endInclusive")
    long countDistinctBuyerIdByCreatedAtBetween(
            @Param("startInclusive") Instant startInclusive,
            @Param("endInclusive") Instant endInclusive
    );

    @Query("select count(distinct subOrder.sellerId) from SubOrderJpaEntity subOrder where subOrder.order.createdAt between :startInclusive and :endInclusive")
    long countDistinctSellerIdByCreatedAtBetween(
            @Param("startInclusive") Instant startInclusive,
            @Param("endInclusive") Instant endInclusive
    );

    @Query("select new com.vnshop.orderservice.infrastructure.persistence.OrderJpaRepository$RevenueByDate(cast(order.createdAt as LocalDate), coalesce(sum(order.finalAmount.amount), 0)) from OrderJpaEntity order where order.createdAt between :startInclusive and :endInclusive group by cast(order.createdAt as LocalDate) order by cast(order.createdAt as LocalDate)")
    List<OrderJpaRepository.RevenueByDate> revenueByDateBetween(
            @Param("startInclusive") Instant startInclusive,
            @Param("endInclusive") Instant endInclusive
    );

    @Query("select new com.vnshop.orderservice.infrastructure.persistence.OrderJpaRepository$TopMetric(item.productId, min(item.name), sum(item.quantity)) from OrderItemJpaEntity item group by item.productId order by sum(item.quantity) desc")
    List<OrderJpaRepository.TopMetric> topProducts(Pageable pageable);

    @Query("select new com.vnshop.orderservice.infrastructure.persistence.OrderJpaRepository$TopMetric(subOrder.sellerId, subOrder.sellerId, coalesce(sum(subOrder.order.finalAmount.amount), 0)) from SubOrderJpaEntity subOrder group by subOrder.sellerId order by coalesce(sum(subOrder.order.finalAmount.amount), 0) desc")
    List<OrderJpaRepository.TopMetric> topSellers(Pageable pageable);
}
