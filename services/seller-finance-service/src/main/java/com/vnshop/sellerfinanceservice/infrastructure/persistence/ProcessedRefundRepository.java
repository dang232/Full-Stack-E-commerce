package com.vnshop.sellerfinanceservice.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedRefundRepository extends JpaRepository<ProcessedRefund, String> {
}
