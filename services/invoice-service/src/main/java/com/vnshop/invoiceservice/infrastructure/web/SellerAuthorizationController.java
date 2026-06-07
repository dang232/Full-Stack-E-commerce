package com.vnshop.invoiceservice.infrastructure.web;

import com.vnshop.invoiceservice.application.SellerAuthorizationService;
import com.vnshop.invoiceservice.domain.entity.SellerAuthorization;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SellerAuthorizationController {

    private final SellerAuthorizationService sellerAuthorizationService;

    /**
     * Activates invoicing authorization for a seller.
     * Body: { "taxCode": "...", "digitalCertId": "..." }
     */
    @PostMapping("/{sellerId}/authorize-invoicing")
    public ResponseEntity<SellerAuthorization> authorize(
            @PathVariable String sellerId,
            @RequestBody AuthorizeRequest request) {
        SellerAuthorization auth = sellerAuthorizationService.authorize(
                sellerId, request.taxCode(), request.digitalCertId());
        return ResponseEntity.ok(auth);
    }

    /**
     * Revokes invoicing authorization for a seller.
     */
    @DeleteMapping("/{sellerId}/revoke-invoicing")
    public ResponseEntity<SellerAuthorization> revoke(@PathVariable String sellerId) {
        SellerAuthorization auth = sellerAuthorizationService.revoke(sellerId);
        return ResponseEntity.ok(auth);
    }

    /**
     * Lists all sellers with ACTIVE authorization.
     */
    @GetMapping("/authorized")
    public ResponseEntity<List<SellerAuthorization>> listAuthorized() {
        return ResponseEntity.ok(sellerAuthorizationService.listAuthorized());
    }

    /**
     * Returns the authorization record for a specific seller,
     * or 404 if no record exists.
     */
    @GetMapping("/{sellerId}/authorization")
    public ResponseEntity<SellerAuthorization> getAuthorization(@PathVariable String sellerId) {
        return sellerAuthorizationService.getAuthorization(sellerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    record AuthorizeRequest(String taxCode, String digitalCertId) {}
}
