package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.Invoice;

import java.util.Optional;

public interface InvoiceRepositoryPort {
    Invoice save(Invoice invoice);

    Optional<Invoice> findById(String invoiceId);

    Optional<Invoice> findBySubOrderId(Long subOrderId);
}
