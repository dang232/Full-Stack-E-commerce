package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Invoice;
import com.vnshop.orderservice.domain.InvoiceAccessDeniedException;
import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.InvoicePdfRendererPort;
import com.vnshop.orderservice.domain.port.out.InvoiceRepositoryPort;
import com.vnshop.orderservice.domain.port.out.InvoiceStoragePort;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class InvoiceUseCase {
    public static final Duration SIGNED_DOWNLOAD_TTL = Duration.ofMinutes(5);
    private static final int INVOICE_VERSION = 1;

    private final OrderRepositoryPort orderRepositoryPort;
    private final InvoiceRepositoryPort invoiceRepositoryPort;
    private final InvoiceStoragePort invoiceStoragePort;
    private final InvoicePdfRendererPort invoicePdfRendererPort;
    private final Clock clock;

    public InvoiceUseCase(
            OrderRepositoryPort orderRepositoryPort,
            InvoiceRepositoryPort invoiceRepositoryPort,
            InvoiceStoragePort invoiceStoragePort,
            InvoicePdfRendererPort invoicePdfRendererPort,
            Clock clock
    ) {
        this.orderRepositoryPort = orderRepositoryPort;
        this.invoiceRepositoryPort = invoiceRepositoryPort;
        this.invoiceStoragePort = invoiceStoragePort;
        this.invoicePdfRendererPort = invoicePdfRendererPort;
        this.clock = clock;
    }

    public Invoice generate(String orderId, Long subOrderId) {
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        SubOrder subOrder = findSubOrder(order, subOrderId);
        return invoiceRepositoryPort.findBySubOrderId(subOrderId)
                .orElseGet(() -> createInvoice(order, subOrder));
    }

    public URI signedDownloadUrl(String invoiceId, InvoiceRequester requester) {
        Invoice invoice = invoiceRepositoryPort.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("invoice not found: " + invoiceId));
        if (!canAccess(invoice, requester)) {
            throw new InvoiceAccessDeniedException("invoice access denied: " + invoiceId);
        }
        return invoiceStoragePort.signedDownloadUrl(invoice.objectKey(), SIGNED_DOWNLOAD_TTL);
    }

    private Invoice createInvoice(Order order, SubOrder subOrder) {
        byte[] pdf = invoicePdfRendererPort.render(order, subOrder, INVOICE_VERSION);
        String checksum = sha256Hex(pdf);
        String id = UUID.randomUUID().toString();
        String objectKey = "invoices/%s/%s/%s.pdf".formatted(order.id(), subOrder.id(), UUID.randomUUID());
        Instant generatedAt = Instant.now(clock);
        invoiceStoragePort.putInvoicePdf(objectKey, pdf, checksum);
        return invoiceRepositoryPort.save(new Invoice(
                id,
                order.id(),
                subOrder.id(),
                order.buyerId(),
                subOrder.sellerId(),
                objectKey,
                checksum,
                INVOICE_VERSION,
                generatedAt
        ));
    }

    private SubOrder findSubOrder(Order order, Long subOrderId) {
        if (subOrderId == null) {
            throw new IllegalArgumentException("subOrderId is required");
        }
        return order.subOrders().stream()
                .filter(subOrder -> subOrderId.equals(subOrder.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("subOrder not found: " + subOrderId));
    }

    private boolean canAccess(Invoice invoice, InvoiceRequester requester) {
        if (requester == null) {
            return false;
        }
        return requester.hasAdminFinanceRole()
                || invoice.buyerId().equals(requester.buyerId())
                || invoice.sellerId().equals(requester.sellerId());
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record InvoiceRequester(String buyerId, String sellerId, boolean hasAdminFinanceRole) {
    }
}
