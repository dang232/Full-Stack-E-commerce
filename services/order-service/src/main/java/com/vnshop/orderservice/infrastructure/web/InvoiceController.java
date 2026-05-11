package com.vnshop.orderservice.infrastructure.web;

import com.vnshop.orderservice.application.InvoiceUseCase;
import com.vnshop.orderservice.infrastructure.config.JwtPrincipalUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {
    private static final String ADMIN_FINANCE_ROLE = "ADMIN_FINANCE";

    private final InvoiceUseCase invoiceUseCase;

    public InvoiceController(InvoiceUseCase invoiceUseCase) {
        this.invoiceUseCase = invoiceUseCase;
    }

    @PostMapping("/orders/{orderId}/sub-orders/{subOrderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvoiceResponse> generate(@PathVariable String orderId, @PathVariable Long subOrderId) {
        return ApiResponse.ok(InvoiceResponse.fromDomain(invoiceUseCase.generate(UUID.fromString(orderId), subOrderId)));
    }

    @GetMapping("/{invoiceId}/download-url")
    public ApiResponse<DownloadUrlResponse> downloadUrl(@PathVariable UUID invoiceId) {
        URI url = invoiceUseCase.signedDownloadUrl(invoiceId, requester());
        return ApiResponse.ok(new DownloadUrlResponse(url, InvoiceUseCase.SIGNED_DOWNLOAD_TTL.toSeconds()));
    }

    private InvoiceUseCase.InvoiceRequester requester() {
        String subject = JwtPrincipalUtil.currentUserId();
        return new InvoiceUseCase.InvoiceRequester(subject, subject, JwtPrincipalUtil.hasRole(ADMIN_FINANCE_ROLE));
    }
}
