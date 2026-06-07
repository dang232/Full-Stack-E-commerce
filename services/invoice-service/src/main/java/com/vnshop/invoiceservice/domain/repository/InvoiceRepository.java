package com.vnshop.invoiceservice.domain.repository;

import com.vnshop.invoiceservice.domain.entity.Invoice;
import com.vnshop.invoiceservice.domain.entity.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByOrderId(UUID orderId);

    List<Invoice> findBySellerIdAndStatus(String sellerId, InvoiceStatus status);

    List<Invoice> findBySellerId(String sellerId);
}
