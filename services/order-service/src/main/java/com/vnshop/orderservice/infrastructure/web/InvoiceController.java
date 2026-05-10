package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.InvoiceUseCase;
import com.vnshop.orderservice.domain.Invoice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BUYER_ID_HEADER = "X-Buyer-Id";
    private static final String SELLER_ID_HEADER = "X-Seller-Id";
    private static final String ROLE_HEADER = "X-Role";
    private static final String ADMIN_FINANCE_ROLE = "ADMIN_FINANCE";

    private final InvoiceUseCase invoiceUseCase;

    public InvoiceController(InvoiceUseCase invoiceUseCase) {
        this.invoiceUseCase = invoiceUseCase;
    }

    @PostMapping("/orders/{orderId}/sub-orders/{subOrderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse generate(@PathVariable String orderId, @PathVariable Long subOrderId) {
        return InvoiceResponse.fromDomain(invoiceUseCase.generate(orderId, subOrderId));
    }

    @GetMapping("/{invoiceId}/download-url")
    public DownloadUrlResponse downloadUrl(
            @PathVariable String invoiceId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = BUYER_ID_HEADER, required = false) String buyerId,
            @RequestHeader(name = SELLER_ID_HEADER, required = false) String sellerId,
            @RequestHeader(name = ROLE_HEADER, required = false) String role
    ) {
        URI url = invoiceUseCase.signedDownloadUrl(invoiceId, requester(userId, buyerId, sellerId, role));
        return new DownloadUrlResponse(url, InvoiceUseCase.SIGNED_DOWNLOAD_TTL.toSeconds());
    }

    private InvoiceUseCase.InvoiceRequester requester(String userId, String buyerId, String sellerId, String role) {
        boolean adminFinance = role != null && ADMIN_FINANCE_ROLE.equalsIgnoreCase(role.trim());
        String currentBuyerId = firstNonBlank(buyerId, userId);
        return new InvoiceUseCase.InvoiceRequester(currentBuyerId, sellerId, adminFinance);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    public record InvoiceResponse(
            String id,
            String orderId,
            Long subOrderId,
            String checksumSha256,
            int version,
            Instant generatedAt
    ) {
        static InvoiceResponse fromDomain(Invoice invoice) {
            return new InvoiceResponse(
                    invoice.id(),
                    invoice.orderId(),
                    invoice.subOrderId(),
                    invoice.checksumSha256(),
                    invoice.version(),
                    invoice.generatedAt()
            );
        }
    }

    public record DownloadUrlResponse(URI url, long expiresInSeconds) {
    }
}
