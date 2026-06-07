package com.vnshop.invoiceservice.application;

import com.vnshop.invoiceservice.domain.entity.Invoice;
import com.vnshop.invoiceservice.domain.entity.InvoiceStatus;
import com.vnshop.invoiceservice.domain.repository.InvoiceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    /**
     * Creates a DRAFT invoice from an order.confirmed event payload.
     * Idempotent: if an invoice already exists for the orderId, returns the existing one.
     */
    @Transactional
    public Invoice createDraftInvoice(UUID orderId, String sellerId, String items, String vatBreakdown, String buyerTaxCode) {
        Optional<Invoice> existing = invoiceRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.debug("Invoice already exists for orderId={} — idempotent no-op", orderId);
            return existing.get();
        }

        Instant now = Instant.now();
        Invoice invoice = Invoice.builder()
                .orderId(orderId)
                .sellerId(sellerId)
                .buyerTaxCode(buyerTaxCode)
                .items(items)
                .vatBreakdown(vatBreakdown)
                .status(InvoiceStatus.DRAFT)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Created DRAFT invoice id={} for orderId={} sellerId={}", saved.getId(), orderId, sellerId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> findByOrderId(UUID orderId) {
        return invoiceRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> findBySeller(String sellerId, InvoiceStatus status) {
        if (status != null) {
            return invoiceRepository.findBySellerIdAndStatus(sellerId, status);
        }
        return invoiceRepository.findBySellerId(sellerId);
    }
}
