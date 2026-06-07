package com.vnshop.invoiceservice.infrastructure.web;

import com.vnshop.invoiceservice.application.InvoiceService;
import com.vnshop.invoiceservice.domain.entity.Invoice;
import com.vnshop.invoiceservice.domain.entity.InvoiceStatus;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /**
     * Returns the invoice for the given orderId.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Invoice> getByOrderId(@PathVariable UUID orderId) {
        return invoiceService.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists invoices filtered by sellerId and optional status.
     */
    @GetMapping
    public ResponseEntity<List<Invoice>> list(
            @RequestParam String sellerId,
            @RequestParam(required = false) InvoiceStatus status) {
        List<Invoice> invoices = invoiceService.findBySeller(sellerId, status);
        return ResponseEntity.ok(invoices);
    }
}
