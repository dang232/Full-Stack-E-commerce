package com.vnshop.paymentservice.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentCallbackLogSpringDataRepository extends JpaRepository<PaymentCallbackLogJpaEntity, String> {
    Optional<PaymentCallbackLogJpaEntity> findFirstByProviderAndEventIdAndSignatureHashAndProcessingStatusIn(String provider, String eventId, String signatureHash, List<String> processingStatuses);

    Optional<PaymentCallbackLogJpaEntity> findFirstByProviderAndPayloadHashAndSignatureHashAndProcessingStatusIn(String provider, String payloadHash, String signatureHash, List<String> processingStatuses);
}
