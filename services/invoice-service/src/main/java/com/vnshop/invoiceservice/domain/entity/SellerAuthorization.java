package com.vnshop.invoiceservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seller_authorizations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "tax_code", nullable = false)
    private String taxCode;

    @Column(name = "digital_cert_id")
    private String digitalCertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuthorizationStatus status;
}
