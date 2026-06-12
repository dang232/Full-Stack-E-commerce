package com.vnshop.invoiceservice.infrastructure.web;

import com.vnshop.invoiceservice.application.InvoiceService;
import com.vnshop.invoiceservice.application.gdt.InvoiceSubmissionService;
import com.vnshop.invoiceservice.domain.entity.Invoice;
import com.vnshop.invoiceservice.domain.entity.InvoiceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final InvoiceSubmissionService invoiceSubmissionService;

    /**
     * Returns the invoice for the given orderId.
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<Invoice> getByOrderId(@PathVariable UUID orderId) {
        return invoiceService.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists invoices filtered by sellerId and optional status.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> generateXml(@PathVariable UUID orderId) {
        String xml = invoiceService.generateXml(orderId);
        return ResponseEntity.ok(xml);
    }

    /**
     * Submits the invoice for the given orderId to the GDT API.
     * The invoice must already have an XML payload (generate via /{orderId}/xml first).
     *
     * POST /api/v1/invoices/{orderId}/submit
     */
    @PostMapping("/{orderId}/submit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Invoice> submit(@PathVariable UUID orderId) {
        Invoice invoice = invoiceSubmissionService.submitToGdt(orderId);
        return ResponseEntity.ok(invoice);
    }

    /**
     * Returns the current GDT submission status for the invoice.
     *
     * GET /api/v1/invoices/{orderId}/gdt-status
     */
    @GetMapping("/{orderId}/gdt-status")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public ResponseEntity<Map<String, Object>> gdtStatus(@PathVariable UUID orderId) {
        return invoiceService.findByOrderId(orderId)
                .map(inv -> ResponseEntity.ok(Map.<String, Object>of(
                        "orderId", inv.getOrderId(),
                        "status", inv.getStatus(),
                        "gdtInvoiceNumber", inv.getGdtInvoiceNumber() != null ? inv.getGdtInvoiceNumber() : "",
                        "gdtVerificationCode", inv.getGdtVerificationCode() != null ? inv.getGdtVerificationCode() : "",
                        "rejectionReason", inv.getRejectionReason() != null ? inv.getRejectionReason() : "",
                        "submittedAt", inv.getSubmittedAt() != null ? inv.getSubmittedAt().toString() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Resubmits a REJECTED invoice to the GDT API after admin correction.
     *
     * POST /api/v1/invoices/{orderId}/resubmit
     */
    @PostMapping("/{orderId}/resubmit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Invoice> resubmit(@PathVariable UUID orderId) {
        Invoice invoice = invoiceSubmissionService.resubmitToGdt(orderId);
        return ResponseEntity.ok(invoice);
    }
}
