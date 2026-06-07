package com.vnshop.invoiceservice.application;

import com.vnshop.invoiceservice.domain.entity.AuthorizationStatus;
import com.vnshop.invoiceservice.domain.entity.SellerAuthorization;
import com.vnshop.invoiceservice.domain.repository.SellerAuthorizationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerAuthorizationService {

    private final SellerAuthorizationRepository repository;

    /**
     * Activates invoicing authorization for a seller.
     * If the seller already has a record it is updated in-place (idempotent upsert).
     */
    @Transactional
    public SellerAuthorization authorize(String sellerId, String taxCode, String digitalCertId) {
        SellerAuthorization auth = repository.findBySellerId(sellerId)
                .map(existing -> {
                    existing.setTaxCode(taxCode);
                    existing.setDigitalCertId(digitalCertId);
                    existing.setStatus(AuthorizationStatus.ACTIVE);
                    existing.setAuthorizedAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> SellerAuthorization.builder()
                        .sellerId(sellerId)
                        .taxCode(taxCode)
                        .digitalCertId(digitalCertId)
                        .status(AuthorizationStatus.ACTIVE)
                        .authorizedAt(Instant.now())
                        .taxDeductionPercent(10)
                        .build());

        SellerAuthorization saved = repository.save(auth);
        log.info("Seller {} authorized for invoicing (taxCode={})", sellerId, taxCode);
        return saved;
    }

    /**
     * Revokes invoicing authorization for a seller.
     *
     * @throws jakarta.persistence.EntityNotFoundException if no authorization record exists
     */
    @Transactional
    public SellerAuthorization revoke(String sellerId) {
        SellerAuthorization auth = repository.findBySellerId(sellerId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "No authorization found for sellerId=" + sellerId));
        auth.setStatus(AuthorizationStatus.REVOKED);
        SellerAuthorization saved = repository.save(auth);
        log.info("Seller {} invoicing authorization revoked", sellerId);
        return saved;
    }

    /** Returns true only when the seller has an ACTIVE authorization. */
    @Transactional(readOnly = true)
    public boolean isAuthorized(String sellerId) {
        return repository.findBySellerId(sellerId)
                .map(a -> AuthorizationStatus.ACTIVE == a.getStatus())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<SellerAuthorization> getAuthorization(String sellerId) {
        return repository.findBySellerId(sellerId);
    }

    @Transactional(readOnly = true)
    public List<SellerAuthorization> listAuthorized() {
        return repository.findAllByStatus(AuthorizationStatus.ACTIVE);
    }
}
