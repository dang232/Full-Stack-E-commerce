package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepositoryPort {
    Invoice save(Invoice invoice);

    Optional<Invoice> findById(UUID invoiceId);

    Optional<Invoice> findBySubOrderId(Long subOrderId);
}
