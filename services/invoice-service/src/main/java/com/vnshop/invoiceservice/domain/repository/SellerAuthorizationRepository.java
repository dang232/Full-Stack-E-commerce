package com.vnshop.invoiceservice.domain.repository;

import com.vnshop.invoiceservice.domain.entity.AuthorizationStatus;
import com.vnshop.invoiceservice.domain.entity.SellerAuthorization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerAuthorizationRepository extends JpaRepository<SellerAuthorization, UUID> {

    Optional<SellerAuthorization> findBySellerId(String sellerId);

    List<SellerAuthorization> findAllByStatus(AuthorizationStatus status);
}
