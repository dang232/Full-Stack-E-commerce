package com.vnshop.invoiceservice.infrastructure.web;

import com.vnshop.invoiceservice.application.InvoiceService;
import com.vnshop.invoiceservice.domain.entity.Invoice;
import com.vnshop.invoiceservice.domain.entity.InvoiceStatus;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * Generates TKHDon XML for the invoice associated with the given orderId.
     * Validates against XSD, persists the XML payload on the invoice, and returns it.
     *
     * POST /api/v1/invoices/{orderId}/xml
     */
    @PostMapping(value = "/{orderId}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> generateXml(@PathVariable UUID orderId) {
        String xml = invoiceService.generateXml(orderId);
        return ResponseEntity.ok(xml);
    }
}
